package yandere;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Oyuncu modelini (ince kollu) kullanir; skin: textures/entity/yuki.png */
public class YandereRenderer extends BipedEntityRenderer<YandereEntity, PlayerEntityModel<YandereEntity>> {
    private static final Identifier TEXTURE = Identifier.of("yandere", "textures/entity/yuki.png");

    public YandereRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true), 0.5f);
    }

    @Override
    public Identifier getTexture(YandereEntity entity) { return TEXTURE; }
}
