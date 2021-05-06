package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.CarFreight;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class CarFreightDefinition extends FreightDefinition {

    private int numSlots;
    private int width;
    private List<String> validCargo;

    public CarFreightDefinition(String defID, JsonObject data) throws Exception {
        super(CarFreight.class, defID, data);

        // Handle null data
        if (validCargo == null) {
            validCargo = new ArrayList<>();
        }
    }

    @Override
    public void parseJson(JsonObject data) throws Exception {
        super.parseJson(data);
        JsonObject freight = data.get("freight").getAsJsonObject();
        this.numSlots = (int) Math.ceil(freight.get("slots").getAsInt() * internal_inv_scale);
        this.width = (int) Math.ceil(freight.get("width").getAsInt() * internal_inv_scale);
        this.validCargo = new ArrayList<>();
        for (JsonElement el : freight.get("cargo").getAsJsonArray()) {
            validCargo.add(el.getAsString());
        }
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        tips.add(GuiText.FREIGHT_CAPACITY_TOOLTIP.toString(this.getInventorySize(gauge)));
        return tips;
    }

    private int[] calcNewSlots(Gauge gauge) {
        int slots = (int) Math.ceil(numSlots * gauge.scale() * Config.ConfigBalance.freightMultiplier);
        if (slots < width) { return new int[] {slots, slots}; }
        int newSlots = 0;
        int newWidth = width;
        for (int i : new int[] {6, 8, 9, 10, 12, width}) {
            int mul = slots + i / 2;
            mul -= mul % i;
            if (Math.abs(slots - mul) <= Math.abs(slots - newSlots)) {
                newSlots = mul;
                newWidth = i;
            }
        }
        return new int[]{newSlots, newWidth};
    }

    public int getInventorySize(Gauge gauge) {
        return calcNewSlots(gauge)[0];
    }

    public int getInventoryWidth(Gauge gauge) {
        return calcNewSlots(gauge)[1];
    }

    @Override
    public boolean acceptsLivestock() {
        return true;
    }
}
