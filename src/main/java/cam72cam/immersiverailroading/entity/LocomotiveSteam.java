package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.inventory.SlotFilter;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.RenderComponentType;
import cam72cam.immersiverailroading.library.ValveGearType;
import cam72cam.immersiverailroading.model.RenderComponent;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import cam72cam.immersiverailroading.registry.Quilling.Chime;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.gui.GuiRegistry;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapper;
import cam72cam.mod.sound.ISound;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.LiquidUtil;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.FluidStack;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.serialization.TagCompound;

import java.util.*;

public class LocomotiveSteam extends Locomotive {
	// PSI
	@TagSync
	@TagField("boiler_psi")
	private float boilerPressure = 0;

	// Celsius
	@TagSync
	@TagField("boiler_temperature")
	private float boilerTemperature;

	@TagSync
	@TagField("pressure_valve")
	private boolean pressureValve = false;
	
	// Map<Slot, TicksToBurn>
	@TagSync
	@TagField(value = "burn_time", mapper = LocomotiveSteam.SlotTagMapper.class)
	private Map<Integer, Integer> burnTime = new HashMap<>();
	@TagSync
	@TagField(value = "burn_max", mapper = LocomotiveSteam.SlotTagMapper.class)
	private Map<Integer, Integer> burnMax = new HashMap<>();

	private Double driverDiameter;
	private float drainRemainder;
	
	public LocomotiveSteam() {
		boilerTemperature = ambientTemperature();
	}

	@Override
	public LocomotiveSteamDefinition getDefinition() {
		return super.getDefinition(LocomotiveSteamDefinition.class);
	}

	@Override
	public GuiRegistry.EntityGUI guiType() {
		return GuiTypes.STEAM_LOCOMOTIVE;
	}

	private double getDriverDiameter() {
		if (driverDiameter == null) {
			driverDiameter = 0d;
			List<RenderComponent> driving = this.getDefinition().getComponents(RenderComponentType.WHEEL_DRIVER_X, gauge);
			if (driving != null) {
				for (RenderComponent driver : driving) {
					driverDiameter = Math.max(driverDiameter, driver.height());
				}
			}
			driving = this.getDefinition().getComponents(RenderComponentType.WHEEL_DRIVER_REAR_X, gauge);
			if (driving != null) {
				for (RenderComponent driver : driving) {
					driverDiameter = Math.max(driverDiameter, driver.height());
				}
			}
		}
		return driverDiameter;
	}
	
	public float getBoilerTemperature() {
		return boilerTemperature;
	}
	private void setBoilerTemperature(float temp) {
		boilerTemperature = temp;
	}
	
	public float getBoilerPressure() {
		return boilerPressure;
	}
	private void setBoilerPressure(float temp) {
		boilerPressure = temp;
	}

	public Map<Integer, Integer> getBurnTime() {
		return burnTime;
	}
	public Map<Integer, Integer> getBurnMax() {
		return burnMax;
	}

	@Override
	protected int getAvailableHP() {
		if (!Config.isFuelRequired(gauge)) {
			return this.getDefinition().getHorsePower(gauge);
		}
		return (int) (this.getDefinition().getHorsePower(gauge) * Math.pow(this.getBoilerPressure() / this.getDefinition().getMaxPSI(gauge), 3));
	}
	
	
	@Override
	public void onDissassemble() {
		super.onDissassemble();
		this.setBoilerTemperature(ambientTemperature());
		this.setBoilerPressure(0);
		
		for (Integer slot : burnTime.keySet()) {
			burnTime.put(slot, 0);
		}
	}

	private double getPhase(int spikes, float offsetDegrees, double perc) {
		if (getDriverDiameter() == 0) {
			return 0;
		}
		double circumference = (getDriverDiameter() * Math.PI);
		double skewDistance = this.distanceTraveled - this.getCurrentSpeed().minecraft() * perc;
		double phase = (skewDistance % circumference)/circumference;
		phase = Math.abs(Math.cos(phase*Math.PI*spikes + Math.toRadians(offsetDegrees)));
		return phase;
	}
	
	private double getPhase(int spikes, float offsetDegrees) {
		if (getDriverDiameter() == 0) {
			return 0;
		}
		double circumference = (getDriverDiameter() * Math.PI);
		double phase = (this.distanceTraveled % circumference)/circumference;
		phase = Math.abs(Math.cos(phase*Math.PI*spikes + Math.toRadians(offsetDegrees)));
		return phase;
	}
	
	private Map<String, Boolean> phaseOn = new HashMap<>();
	private List<ISound> sndCache = new ArrayList<>();
	private int sndCacheId = 0;
	private ISound whistle;
	private List<ISound> chimes = new ArrayList<>();
	private float pullString = 0;
	private float soundDampener = 0;
	private ISound idle;
	private ISound pressure;

	private int tickMod = 0;
	@Override
	public void onTick() {
		super.onTick();
		
		if (this.getTickCount() < 2) {
			// Prevent explosions
			return;
		}

		if (getWorld().isClient) {
			// Particles and Sound
			
			if (ConfigSound.soundEnabled) {
				if (this.sndCache.size() == 0) {
					this.whistle = ImmersiveRailroading.newSound(this.getDefinition().whistle, false, 150, this.soundGauge());
					this.bell = ImmersiveRailroading.newSound(this.getDefinition().bell, true, 150, this.soundGauge());
					whistle.setPitch(1);
					
					if (this.getDefinition().quill != null) {
						for (Chime chime : this.getDefinition().quill.chimes) {
							this.chimes.add(ImmersiveRailroading.newSound(chime.sample, true, 150, this.soundGauge()));
						}
					}
	
					for (int i = 0; i < 8; i ++) {
						sndCache.add(ImmersiveRailroading.newSound(this.getDefinition().chuff, false, 80, this.soundGauge()));
					}
					
					this.idle = ImmersiveRailroading.newSound(this.getDefinition().idle, true, 40, this.soundGauge());
					idle.setVolume(0.1f);
					this.pressure = ImmersiveRailroading.newSound(this.getDefinition().pressure, true, 40, this.soundGauge());
					pressure.setVolume(0.3f);
				}
				
				if (hornTime < 1) {
					pullString = 0;
					soundDampener = 0;
					for (ISound chime : chimes) {
						if (chime.isPlaying()) {
							chime.stop();
						}
					}
				} else {
					if (this.getBoilerPressure() > 0 || !Config.isFuelRequired(gauge)) {
						if (this.getDefinition().quill == null) {
							if (!this.whistle.isPlaying()) {
								this.whistle.play(getPosition());
							}
						} else {
							float maxDelta = 1/20f;
							float delta = 0;
							if (hornTime > 5) {
								if (soundDampener < 0.4) {
									soundDampener = 0.4f;
								}
								if (soundDampener < 1) {
									soundDampener += 0.1;
								}
								if (hornPlayer != null) {
									for (Entity pass : this.getPassengers()) {
										if (!pass.getUUID().equals(hornPlayer)) {
											continue;
										}
										
										float newString = (pass.getRotationPitch()+90) / 180;
										delta = newString - pullString;
									}
								} else {
									delta = (float)this.getDefinition().quill.maxPull-pullString;
								}
							} else {
								if (soundDampener > 0) {
									soundDampener -= 0.07;
								}
								// Player probably released key or has net lag
								delta = -pullString; 
							}
							
							if (pullString == 0) {
								pullString += delta*0.55;
							} else {
								pullString += Math.max(Math.min(delta, maxDelta), -maxDelta);
							}
							pullString = Math.min(pullString, (float)this.getDefinition().quill.maxPull);
							
							for (int i = 0; i < this.getDefinition().quill.chimes.size(); i++) {
								ISound sound = this.chimes.get(i);
								Chime chime = this.getDefinition().quill.chimes.get(i);
								
								double perc = pullString;
								// Clamp to start/end
								perc = Math.min(perc, chime.pull_end);
								perc -= chime.pull_start;
								
								//Scale to clamped range
								perc /= chime.pull_end - chime.pull_start;
								
								if (perc > 0) {
									
									double pitch = (chime.pitch_end - chime.pitch_start) * perc + chime.pitch_start;
	
									sound.setPitch((float) pitch);
									sound.setVolume((float) (perc * soundDampener));
									
									if (!sound.isPlaying()) {
										sound.play(getPosition());
									}
								} else {
									if (sound.isPlaying()) {
										sound.stop();
									}
								}
							}
						}
					}
				}
				
				if (this.getBoilerTemperature() > this.ambientTemperature() + 5) {
					if (!idle.isPlaying()) {
						idle.play(getPosition());
					}
				} else {
					if (idle.isPlaying()) {
						idle.stop();
					}
				}
			}
			
			double phase;
			
			Vec3d fakeMotion = this.getVelocity();
			
			List<RenderComponent> smokes = this.getDefinition().getComponents(RenderComponentType.PARTICLE_CHIMNEY_X, gauge);
			if (smokes != null && ConfigGraphics.particlesEnabled) {
				phase = getPhase(4, 0);
				for (RenderComponent smoke : smokes) {
					Vec3d particlePos = this.getPosition().add(VecUtil.rotateWrongYaw(smoke.center(), this.getRotationYaw() + 180));
					particlePos = particlePos.subtract(fakeMotion);
					if (this.getTickCount() % 1 == 0 ) {
						float darken = 0;
						float thickness = Math.abs(this.getThrottle())/2;
						for (int i : burnTime.values()) {
							darken += i >= 1 ? 1 : 0;
						}
						if (darken == 0 && Config.isFuelRequired(gauge)) {
							break;
						}
						darken /= this.getInventorySize() - 2.0;
						darken *= 0.5;
						
						double smokeMod = Math.min(1, Math.max(0.2, Math.abs(this.getCurrentSpeed().minecraft())*2));
						
						int lifespan = (int) (200 * (1 + Math.abs(this.getThrottle())) * smokeMod * gauge.scale());
						//lifespan *= size;
						
						float verticalSpeed = 0.5f;//(0.5f + Math.abs(this.getThrottle())) * (float)gauge.scale();
						
						double size = smoke.width() * (0.8 + smokeMod);
						if (phase != 0 && Math.abs(this.getThrottle()) > 0.01 && Math.abs(this.getCurrentSpeed().metric()) / gauge.scale() < 30) {
							double phaseSpike = Math.pow(phase, 8);
							size *= 1 + phaseSpike*1.5;
							verticalSpeed *= 1 + phaseSpike/2;
						}
						
						particlePos = particlePos.subtract(fakeMotion);

						addSmoke(particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + verticalSpeed, fakeMotion.z), lifespan , darken, thickness, size);
					}
				}
			}
			
			List<RenderComponent> whistles = this.getDefinition().getComponents(RenderComponentType.WHISTLE, gauge);
			if (	whistles != null &&
					(hornTime != 0 || whistle != null && whistle.isPlaying()) &&
					(this.getBoilerPressure() > 0 || !Config.isFuelRequired(gauge))
				) {
				for (RenderComponent whistle : whistles) {
					Vec3d particlePos = this.getPosition().add(VecUtil.rotateWrongYaw(whistle.center(), this.getRotationYaw() + 180));
					particlePos = particlePos.subtract(fakeMotion);
					
					float darken = 0;
					float thickness = 1;
					double smokeMod = Math.min(1, Math.max(0.2, Math.abs(this.getCurrentSpeed().minecraft())*2));
					int lifespan = (int) (40 * (1 + smokeMod * gauge.scale()));
					float verticalSpeed = 0.8f;
					double size = 0.3 * (0.8 + smokeMod);

					particlePos = particlePos.subtract(fakeMotion);

					addSmoke(particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + verticalSpeed, fakeMotion.z), lifespan , darken, thickness, size);
				}
			}
			List<RenderComponent> pistons = this.getDefinition().getComponents(RenderComponentType.PISTON_ROD_SIDE, gauge);
			double csm = Math.abs(this.getCurrentSpeed().metric()) / gauge.scale();
			if (pistons != null && (this.getBoilerPressure() > 0 || !Config.isFuelRequired(gauge))) {
				for (RenderComponent piston : pistons) {
					float phaseOffset;
					double tickDelt;
					switch (piston.side) {
					case "LEFT":
						tickDelt = 2;
						phaseOffset = 45+90;
						break;
					case "RIGHT":
						tickDelt = 2;
						phaseOffset = this.getDefinition().getValveGear() != ValveGearType.TRI_WALSCHAERTS ? -45+90 : 45+90-240;
						break;
					case "CENTER":
						tickDelt = 2;
						phaseOffset = 45+90-120;
						break;
					case "LEFT_FRONT":
						tickDelt = 1;
						phaseOffset = 45+90;
						break;
					case "RIGHT_FRONT":
						tickDelt = 1;
						phaseOffset = -45+90;
						break;
					case "LEFT_REAR":
						tickDelt = 1;
						phaseOffset = 90;
						break;
					case "RIGHT_REAR":
						tickDelt = 1;
						phaseOffset = 0;
						break;
					default:
						continue;
					}
					
					phase = this.getPhase(2, phaseOffset);
					double phaseSpike = Math.pow(phase, 4);
					
					if (phaseSpike >= 0.6 && csm > 0.1 && csm  < 20 && ConfigGraphics.particlesEnabled) {
						Vec3d particlePos = this.getPosition().add(VecUtil.rotateWrongYaw(piston.min(), this.getRotationYaw() + 180));
						double accell = 0.3 * gauge.scale();
						if (piston.side.contains("LEFT")) {
							accell = -accell;
						}
						if (piston.side.contains("CENTER") ) {
							accell = 0;
						}
						Vec3d sideMotion = fakeMotion.add(VecUtil.fromWrongYaw(accell, this.getRotationYaw()+90));
						addSmoke(particlePos, new Vec3d(sideMotion.x, sideMotion.y+0.01, sideMotion.z), 80, 0, 0.6f, 0.2);
					}
					
					if (!ConfigSound.soundEnabled) {
						continue;
					}
					
					String key = piston.side;
					if (!phaseOn.containsKey(key)) {
						phaseOn.put(key, false);
					}
					
					for (int i = 0; i < 10; i++) {
						phase = this.getPhase(2, phaseOffset + 45, 1-i/10.0);
						
						if (!phaseOn.get(key)) {
							if (phase > 0.5) {
						    	double speed = Math.abs(getCurrentSpeed().minecraft());
						    	double maxSpeed = Math.abs(getDefinition().getMaxSpeed(gauge).minecraft());
						    	float volume = (float) Math.max(1-speed/maxSpeed, 0.3) * Math.abs(this.getThrottle());
						    	volume = (float) Math.sqrt(volume);
						    	double fraction = 3;
						    	float pitch = 0.8f + (float) (speed/maxSpeed/fraction);
						    	float delta = (8-tickMod) / 200.0f;
						    	ISound snd = sndCache.get(sndCacheId);
						    	snd.setPitch(pitch + delta);
						    	snd.setVolume(volume + delta);
						    	snd.play(getPosition());
						    	sndCacheId++;
						    	sndCacheId = sndCacheId % sndCache.size();
								phaseOn.put(key, true);

								tickMod += tickDelt;
								if (tickMod > 8) {
									tickMod = 0;
								}
							}
						} else {
							if (phase < 0.5) {
								phaseOn.put(key, false);
							}
						}
					}
				}
			}
			
			List<RenderComponent> steams = this.getDefinition().getComponents(RenderComponentType.PRESSURE_VALVE_X, gauge);
			if (steams != null && (pressureValve && Config.isFuelRequired(gauge))) {
				if (ConfigSound.soundEnabled && ConfigSound.soundPressureValve) {
					if (!pressure.isPlaying()) {
						pressure.play(getPosition());
					}
				}
				if (ConfigGraphics.particlesEnabled) {
					for (RenderComponent steam : steams) {
						Vec3d particlePos = this.getPosition().add(VecUtil.rotateWrongYaw(steam.center(), this.getRotationYaw() + 180));
						particlePos = particlePos.subtract(fakeMotion);
						addSmoke(particlePos, new Vec3d(fakeMotion.x, fakeMotion.y + 0.2 * gauge.scale(), fakeMotion.z),40, 0, 0.2f, steam.width());
					}
				}
			} else {
				if (ConfigSound.soundEnabled && pressure.isPlaying()) {
					pressure.stop();
				}
			}
			
			if (ConfigSound.soundEnabled) {
				// Update sound positions
				if (whistle.isPlaying()) {
					whistle.setPosition(getPosition());
					whistle.setVelocity(getVelocity());
					whistle.update();
				}
				for (ISound chime : chimes) {
					if (chime.isPlaying()) {
						chime.setPosition(getPosition());
						chime.setVelocity(getVelocity());
						chime.update();
					}
				}
				if (idle.isPlaying()) {
					idle.setPosition(getPosition());
					idle.setVelocity(getVelocity());
					idle.update();
				}
				if (pressure.isPlaying()) {
					pressure.setPosition(getPosition());
					pressure.setVelocity(getVelocity());
					pressure.update();
				}
				for (ISound snd : sndCache) {
					if (snd.isPlaying()) {
						snd.setPosition(getPosition());
						snd.setVelocity(getVelocity());
						snd.update();
					}
				}
			}
			
			return;
		}
		
		if (!this.isBuilt()) {
			return;
		}

		EntityCoupleableRollingStock stock = this;
		CouplerType coupler = CouplerType.BACK;
		while (coupler != null && stock.getCoupled(coupler) instanceof Tender) {
			Tender tender = (Tender) stock.getCoupled(coupler);

			// Only drain 10mb at a time from the tender
			int desiredDrain = 10;
			if (getTankCapacity().MilliBuckets() - getServerLiquidAmount() >= 10) {
				theTank.drain(tender.theTank, desiredDrain, false);
			}

			if (this.getTickCount() % 20 == 0 && this.getDefinition().tender_auto_feed) {
				// Top off stacks
				for (int slot = 2; slot < this.cargoItems.getSlotCount(); slot ++) {
					if (BurnUtil.getBurnTime(this.cargoItems.get(slot)) != 0) {
						for (int tenderSlot = 0; tenderSlot < tender.cargoItems.getSlotCount(); tenderSlot ++) {
							if (this.cargoItems.get(slot).is(tender.cargoItems.get(tenderSlot))) {
								if (this.cargoItems.get(slot).getLimit() > this.cargoItems.get(slot).getCount()) {
									ItemStack extracted = tender.cargoItems.extract(tenderSlot, 1, false);
									this.cargoItems.insert(slot, extracted, false);
								}
							}
						}
					}
				}
			}
			coupler = tender.getCouplerFor(stock);
			if (coupler == null) {
				break;
			}
			coupler = coupler.opposite();
			stock = tender;
		}
		
		float boilerTemperature = getBoilerTemperature();
		float boilerPressure = getBoilerPressure();
		float waterLevelMB = this.getLiquidAmount();
		int burningSlots = 0;
		float waterUsed = 0;

		if (boilerPressure < 0) {
			boilerPressure = 0;
		}
		
		if (this.getLiquidAmount() > 0) {
			for (int slot = 2; slot < this.cargoItems.getSlotCount(); slot ++) {
				int remainingTime = burnTime.getOrDefault(slot, 0);
				if (remainingTime <= 0) {
					ItemStack stack = this.cargoItems.get(slot);
					if (stack.getCount() <= 0 || BurnUtil.getBurnTime(stack) == 0) {
						continue;
					}
					remainingTime = (int) (BurnUtil.getBurnTime(stack) /gauge.scale() * (Config.ConfigBalance.locoSteamFuelEfficiency / 100.0));
					burnTime.put(slot, remainingTime);
					burnMax.put(slot, remainingTime);
					stack.setCount(stack.getCount()-1);
					this.cargoItems.set(slot, stack);
				} else {
					burnTime.put(slot, remainingTime - 1);
				}
				burningSlots += 1;
			}
		}
		
		double energyKCalDeltaTick = 0;
		
		if (burningSlots != 0 && this.getLiquidAmount() > 0) {
			energyKCalDeltaTick += burningSlots * coalEnergyKCalTick();
		}

		// Assume the boiler is a cube...
		double boilerVolume = this.getTankCapacity().Buckets();
		double boilerEdgeM = Math.pow(boilerVolume, 1.0/3.0);
		double boilerAreaM = 6 * Math.pow(boilerEdgeM, 2);

		if (boilerTemperature > 0) {
			// Decrease temperature due to heat loss
			// Estimate Kw emitter per m^2: (TdegC/10)^2 / 100
			// TODO consider ambientTemperature
			double radiatedKwHr = Math.pow(boilerTemperature/10, 2) / 100 * boilerAreaM * 2;
			double radiatedKCalHr = radiatedKwHr * 859.85;
			double radiatedKCalTick = radiatedKCalHr / 60 / 60 / 20 * ConfigBalance.locoHeatTimeScale;
			energyKCalDeltaTick -= radiatedKCalTick / 1000;
		}
		
		if (energyKCalDeltaTick != 0) {
			// Change temperature
			// 1 KCal raises 1KG water at STP 1 degree
			// 1 KG of water == 1 m^3 of water 
			// TODO what happens when we change liters per mb FluidQuantity.FromMillibuckets((int) waterLevelMB).Liters()
			//  +1 prevents div by zero
			boilerTemperature += energyKCalDeltaTick / ((waterLevelMB + 1) / 1000);
		}
		
		if (boilerTemperature > 100) {
			// Assume linear relationship between temperature and pressure
			float heatTransfer = boilerTemperature - 100;
			boilerPressure += heatTransfer;

			if (this.getPercentLiquidFull() > 25) {
				boilerTemperature -= heatTransfer;
			}
			
			// Pressure relief valve
			int maxPSI = this.getDefinition().getMaxPSI(gauge);
			pressureValve = boilerPressure > maxPSI;
			if (boilerPressure > maxPSI) {
				waterUsed += boilerPressure - maxPSI;
				boilerPressure = maxPSI;
			}
		} else {
			if (boilerPressure > 0) {
				// Reduce pressure by needed temperature
				boilerPressure = Math.max(0, boilerPressure - (100 - boilerTemperature));
				boilerTemperature = 100;
			}

			pressureValve = false;
		}
		
		float throttle = Math.abs(getThrottle());
		if (throttle != 0 && boilerPressure > 0) {
			double burnableSlots = this.cargoItems.getSlotCount()-2;
			double maxKCalTick = burnableSlots * coalEnergyKCalTick();
			double maxPressureTick = maxKCalTick / (this.getTankCapacity().MilliBuckets() / 1000);
			maxPressureTick = maxPressureTick * 0.8; // 20% more pressure gen energyCapability to balance heat loss
			
			float delta = (float) (throttle * maxPressureTick);
			
			boilerPressure = Math.max(0, boilerPressure - delta);
			waterUsed += delta;
		}
		
		if (waterUsed != 0) {
			waterUsed *= Config.ConfigBalance.locoWaterUsage;
			waterUsed += drainRemainder;
			if (waterUsed > 0 && theTank.getContents() != null) {
				theTank.drain(new FluidStack(theTank.getContents().getFluid(), (int) Math.floor(waterUsed)), false);
				drainRemainder = waterUsed % 1;
			}
		}
		
		setBoilerPressure(boilerPressure);
		setBoilerTemperature(Math.max(boilerTemperature, ambientTemperature()));

		if (boilerPressure > this.getDefinition().getMaxPSI(gauge) * 1.1 || (boilerPressure > this.getDefinition().getMaxPSI(gauge) * 0.5 && boilerTemperature > 150)) {
			// 10% over max pressure OR
			// Half max pressure and high boiler temperature
			//EXPLODE

			Vec3d pos = this.getPosition();
			if (Config.ConfigDamage.explosionsEnabled) {
				this.createExplosion(pos, boilerPressure/5, Config.ConfigDamage.explosionEnvDamageEnabled);
			}
			getWorld().removeEntity(this);
		}
	}

	@Override
	public void onRemoved() {
		super.onRemoved();

		for (ISound chime : chimes) {
			chime.stop();
		}
		
		if (idle != null) {
			idle.stop();
		}
		if (pressure != null) {
			pressure.stop();
		}
	}

	@Override
	protected void initContainerFilter() {
		cargoItems.filter.clear();
		this.cargoItems.filter.put(0, SlotFilter.FLUID_CONTAINER);
		this.cargoItems.filter.put(1, SlotFilter.FLUID_CONTAINER);
		this.cargoItems.defaultFilter = SlotFilter.BURNABLE;
	}

	@Override
	public int getInventorySize() {
		return this.getDefinition().getInventorySize(gauge) + 2;
	}
	
	public int getInventoryWidth() {
		return this.getDefinition().getInventoryWidth(gauge);
	}
	
	@Override
	protected int[] getContainerInputSlots() {
		return new int[] { 0 };
	}
	@Override
	protected int[] getContainertOutputSlots() {
		return new int[] { 1 };
	}

	@Override
	public FluidQuantity getTankCapacity() {
		return this.getDefinition().getTankCapacity(gauge);
	}

	@Override
	public List<Fluid> getFluidFilter() {
		return LiquidUtil.getWater();
	}

	private double coalEnergyKCalTick() {
		// Coal density = 800 KG/m3 (engineering toolbox)
		double coalEnergyDensity = 30000; // KJ/KG (engineering toolbox)
		double coalEnergyKJ = coalEnergyDensity / 9; // Assume each slot is burning 1/9th of a coal block
		double coalEnergyBTU = coalEnergyKJ * 0.958; // 1 KJ = 0.958 BTU
		double coalEnergyKCal = coalEnergyBTU / (3.968 * 1000); // 3.968 BTU = 1 KCal
		double coalBurnTicks = 1600; // This is a bit of fudge
		return coalEnergyKCal / coalBurnTicks * ConfigBalance.locoHeatTimeScale;
	}

	private static class SlotTagMapper implements TagMapper<Map<Integer, Integer>> {
		@Override
		public TagAccessor<Map<Integer, Integer>> apply(Class<Map<Integer, Integer>> type, String fieldName, TagField tag) {
			return new TagAccessor<>(
					(d, o) -> d.setMap(fieldName, o, Objects::toString, i -> new TagCompound().setInteger("val", i)),
					d -> {
						Map<Integer, Integer> found = d.getMap(fieldName, Integer::parseInt, t -> t.getInteger("val"));
						if (found == null) {
							found = new HashMap<Integer, Integer>();
						}
						return found;
					}
			);
		}
	}
}
