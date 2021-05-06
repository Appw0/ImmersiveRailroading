package cam72cam.immersiverailroading.render.multiblock;

import java.util.ArrayList;
import java.util.List;

import cam72cam.immersiverailroading.multiblock.SmallCastingMultiblock.SmallCastingInstance;
import cam72cam.mod.render.OpenGL;
import cam72cam.mod.resource.Identifier;
import org.lwjgl.opengl.GL11;

import cam72cam.mod.model.obj.OBJModel;
import cam72cam.immersiverailroading.multiblock.CastingMultiblock.CastingInstance;
import cam72cam.mod.render.obj.OBJRender;
import cam72cam.immersiverailroading.tile.TileMultiblock;

public class SmallCastingRender implements IMultiblockRender {
    private OBJRender renderer;
    private List<String> flowing_steel;
    private List<String> steel_level;
    private List<String> rest;

    @Override
    public void render(TileMultiblock te, float partialTicks) {
        if (renderer == null) {
            try {
                this.renderer = new OBJRender(new OBJModel(new Identifier("immersiverailroading:models/multiblocks/small_casting_machine.obj"), -0.1f, null));
                flowing_steel = new ArrayList<>();
                steel_level = new ArrayList<>();
                rest = new ArrayList<>();
                for (String name : renderer.model.groups.keySet()) {
                    if (name.contains("FLOWING_STEEL")) {
                        flowing_steel.add(name);
                    } else if (name.contains("STEEL_LEVEL")) {
                        steel_level.add(name);
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
            GL11.glTranslated(-2.5, 0, 4.5);
            SmallCastingInstance tmb = (SmallCastingInstance) te.getMultiblock();
            if (tmb.isPouring()) {
                renderer.drawGroups(flowing_steel);
            }
            double steelLevel = tmb.getSteelLevel();
            if (steelLevel != 0) {
                try (OpenGL.With m = OpenGL.matrix()) {
                    GL11.glTranslated(0, steelLevel * 0.8, 0);
                    renderer.drawGroups(steel_level);
                }
            }
            renderer.drawGroups(rest);
        }
    }
}
