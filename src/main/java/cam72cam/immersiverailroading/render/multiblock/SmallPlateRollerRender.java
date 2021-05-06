package cam72cam.immersiverailroading.render.multiblock;

import cam72cam.immersiverailroading.tile.TileMultiblock;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.render.OpenGL;
import cam72cam.mod.render.obj.OBJRender;
import cam72cam.mod.resource.Identifier;
import org.lwjgl.opengl.GL11;

public class SmallPlateRollerRender implements IMultiblockRender {
	private OBJRender renderer;

	@Override
	public void render(TileMultiblock te, float partialTicks) {
		if (renderer == null) {
			try {
				this.renderer = new OBJRender(new OBJModel(new Identifier("immersiverailroading:models/multiblocks/small_plate_rolling_machine.obj"), -0.1f, null));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try (OpenGL.With matrix = OpenGL.matrix(); OpenGL.With tex = renderer.bindTexture()) {
			GL11.glTranslated(0.5, 0, 0.5);
			GL11.glRotated(te.getRotation() - 90, 0, 1, 0);
			GL11.glTranslated(-1.5, 0, 3.5);
			renderer.draw();
		}
	}
}
