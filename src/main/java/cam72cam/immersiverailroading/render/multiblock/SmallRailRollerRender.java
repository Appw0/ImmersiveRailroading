package cam72cam.immersiverailroading.render.multiblock;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.multiblock.RailRollerMultiblock.RailRollerInstance;
import cam72cam.immersiverailroading.multiblock.SmallRailRollerMultiblock.SmallRailRollerInstance;
import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.render.OpenGL;
import cam72cam.mod.render.obj.OBJRender;
import cam72cam.mod.resource.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class SmallRailRollerRender implements IMultiblockRender {
	private OBJRender renderer;
	private List<String> input1;
	private List<String> input2;
	private List<String> output1;
	private List<String> output2;
	private List<String> rest;

	@Override
	public void render(TileMultiblock te, float partialTicks) {
		if (renderer == null) {
			try {
				this.renderer = new OBJRender(new OBJModel(new Identifier(ImmersiveRailroading.MODID, "models/multiblocks/small_rail_machine.obj"), -0.1f, null));
				input1 = new ArrayList<>();
				input2 = new ArrayList<>();
				output1 = new ArrayList<>();
				output2 = new ArrayList<>();
				rest = new ArrayList<>();
				for (String name : renderer.model.groups.keySet()) {
					if (name.contains("INPUT_CAST_1")) {
						input1.add(name);
					} else if (name.contains("INPUT_CAST_2")) {
						input2.add(name);
					} else if (name.contains("OUTPUT_RAIL_1")) {
						output1.add(name);
					} else if (name.contains("OUTPUT_RAIL_2")) {
						output2.add(name);
					} else {
						rest.add(name);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try (OpenGL.With matrix = OpenGL.matrix(); OpenGL.With tex = renderer.bindTexture()) {
			GL11.glTranslated(0.5, 0, 0.5);
			GL11.glRotated(te.getRotation() - 90, 0, 1, 0);
			GL11.glTranslated(-0.5, 0, 0.5);

			SmallRailRollerInstance tmb = (SmallRailRollerInstance) te.getMultiblock();
			int progress = tmb.getCraftProgress();

//			if (progress != 0) {
//				try (OpenGL.With m = OpenGL.matrix()) {
//					GL11.glTranslated(0, 0, progress * 0.075);
//					// 7.5z
//					renderer.drawGroups(input1);
//				}
//			}
			try (OpenGL.With m = OpenGL.matrix()) {
				// 7.5
				if (progress != 0) {
					GL11.glTranslated(0, 0, (progress - 100) * 0.075);
					if (progress > 63) {
						renderer.drawGroups(input2);
					} else {
						renderer.drawGroups(output2);
					}

					if (progress > 34) {
						renderer.drawGroups(input1);
					} else {
						renderer.drawGroups(output1);
					}
				} else if (tmb.outputFull()) {
					GL11.glTranslated(0, 0, -7.5);
					renderer.drawGroups(output1);
					renderer.drawGroups(output2);
				}
			}

			renderer.drawGroups(rest);
		}
	}
}
