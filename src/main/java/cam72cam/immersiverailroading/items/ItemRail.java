package cam72cam.immersiverailroading.items;

import java.util.Collections;
import java.util.List;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.mod.item.CreativeTab;
import cam72cam.mod.item.CustomItem;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.serialization.TagField;

public class ItemRail extends CustomItem {
	public ItemRail() {
		super(ImmersiveRailroading.MODID, "item_rail_part");
	}

	@Override
	public int getStackSize() {
		return 64;
	}

	@Override
	public List<CreativeTab> getCreativeTabs() {
		return Collections.singletonList(ItemTabs.MAIN_TAB);
	}

	@Override
	public List<String> getTooltip(ItemStack stack) {
        return Collections.singletonList(GuiText.GAUGE_TOOLTIP.toString(new Data(stack).gauge));
    }

	public static class Data extends ItemDataSerializer {
		@TagField("gauge")
		public Gauge gauge;

		public Data(ItemStack stack) {
			super(stack);
			if (gauge == null) {
				gauge = Gauge.from(Gauge.STANDARD);
			}
		}
	}
}
