package com.armaninyow.beegui.client;

import com.armaninyow.beegui.BeeGUI;
import com.armaninyow.beegui.BeeGuiScreenTracker;
import com.armaninyow.beegui.network.BeeGuiPacket;
import com.armaninyow.beegui.network.BeeGuiRefreshPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

// 1.21.5
@Environment(EnvType.CLIENT)
public class BeeManagementScreen extends Screen {

	private static final Identifier CONTAINER_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/container.png");
	private static final Identifier HONEY_BAR_BG_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/honey_bar_background.png");
	private static final Identifier HONEY_BAR_FG_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/honey_bar_progress.png");

	private static final int[] HEALTH_BAR_COLORS = {
		0xFFFF0000, 0xFFFF4000, 0xFFFF8000, 0xFFFFBF00, 0xFFFFFF00,
		0xFFBFFF00, 0xFF80FF00, 0xFF40FF00, 0xFF00FF00,
	};
	private static final int HEALTH_BAR_WIDTH  = 10;
	private static final int HEALTH_BAR_HEIGHT = 2;
	private static final int HEALTH_BAR_SHADOW = 0xFF000000;

	private static final int PNG_WIDTH  = 92;
	private static final int PNG_HEIGHT = 58;
	private static final int TITLE_COLOR = 0xFF3F3F3F;

	private static final int[] SLOT_X  = {8, 34, 60};
	private static final int   SLOT_Y  = 17;
	private static final int   SLOT_SIZE = 24;

	private static final int[] HOVER_X1 = { 8, 34, 60};
	private static final int[] HOVER_X2 = {31, 57, 83};
	private static final int   HOVER_Y1 = 17;
	private static final int   HOVER_Y2 = 40;

	private static final int BEE_RENDER_SIZE = 20;
	private static final int BEE_Y_OFFSET    = 36;

	private static final int HONEY_BAR_Y      = 46;
	private static final int HONEY_BAR_WIDTH  = 46;
	private static final int HONEY_BAR_HEIGHT = 5;
	private static final int HONEY_BG_TEX_W   = 46;
	private static final int HONEY_BG_TEX_H   = 5;
	private static final int[] HONEY_SEGMENT_WIDTHS = {0, 10, 19, 28, 37, 46};

	private static final long REFRESH_INTERVAL_MS = 1000;

	private final BlockPos pos;
	private boolean isHive;
	private int honeyLevel;
	private List<BeeGuiPacket.BeeInfo> bees;

	private int guiLeft;
	private int guiTop;
	private int hoveredSlot     = -1;
	private boolean honeyBarHovered = false;
	private long lastRefreshTime = 0;

	private final List<BeeEntity> beeEntities = new ArrayList<>();

	public BeeManagementScreen(boolean isHive, int honeyLevel, List<BeeGuiPacket.BeeInfo> bees, BlockPos pos) {
		super(Text.empty());
		this.isHive     = isHive;
		this.honeyLevel = honeyLevel;
		this.bees       = bees;
		this.pos        = pos;
	}

	public void updateData(boolean isHive, int honeyLevel, List<BeeGuiPacket.BeeInfo> bees) {
		this.isHive     = isHive;
		this.honeyLevel = honeyLevel;
		this.bees       = bees;
		rebuildEntities();
	}

	public BlockPos getPos() { return pos; }

	@Override
	protected void init() {
		super.init();
		this.guiLeft = (this.width  - PNG_WIDTH)  / 2;
		this.guiTop  = (this.height - PNG_HEIGHT) / 2;
		rebuildEntities();
		BeeGuiScreenTracker.openScreenPositions.add(pos);
	}

	@Override
	public void removed() {
		super.removed();
		BeeGuiScreenTracker.openScreenPositions.remove(pos);
	}

	private void rebuildEntities() {
		beeEntities.clear();
		World world = MinecraftClient.getInstance().world;
		if (world == null) return;
		for (BeeGuiPacket.BeeInfo info : bees) {
			BeeEntity entity = EntityType.BEE.create(world, SpawnReason.LOAD);
			if (entity == null) continue;
			if (info.isBaby())    entity.setBreedingAge(-24000);
			if (info.hasNectar()) entity.setHasNectar(true);
			beeEntities.add(entity);
		}
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0x80000000);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
			ClientPlayNetworking.send(new BeeGuiRefreshPacket(pos));
			lastRefreshTime = now;
		}

		// Draw background first, then our content on top
		super.render(context, mouseX, mouseY, delta);

		hoveredSlot     = -1;
		honeyBarHovered = false;
		for (int i = 0; i < 3; i++) {
			int wx1 = guiLeft + HOVER_X1[i];
			int wx2 = guiLeft + HOVER_X2[i];
			int wy1 = guiTop  + HOVER_Y1;
			int wy2 = guiTop  + HOVER_Y2;
			if (mouseX >= wx1 && mouseX <= wx2 && mouseY >= wy1 && mouseY <= wy2) {
				if (i < bees.size()) hoveredSlot = i;
				break;
			}
		}
		int honeyBarX = guiLeft + (PNG_WIDTH - HONEY_BAR_WIDTH) / 2;
		int honeyBarY = guiTop  + HONEY_BAR_Y;
		if (mouseX >= honeyBarX && mouseX < honeyBarX + HONEY_BAR_WIDTH
			&& mouseY >= honeyBarY && mouseY < honeyBarY + HONEY_BAR_HEIGHT) {
			honeyBarHovered = true;
		}

		context.drawTexture(RenderLayer::getGuiTextured, CONTAINER_TEXTURE,
			guiLeft, guiTop,
			0, 0,
			PNG_WIDTH, PNG_HEIGHT,
			PNG_WIDTH, PNG_HEIGHT);

		context.drawTexture(RenderLayer::getGuiTextured, HONEY_BAR_BG_TEXTURE,
			honeyBarX, honeyBarY,
			0, 0,
			HONEY_BAR_WIDTH, HONEY_BAR_HEIGHT,
			HONEY_BG_TEX_W, HONEY_BG_TEX_H);

		int progressWidth = HONEY_SEGMENT_WIDTHS[Math.max(0, Math.min(honeyLevel, 5))];
		if (progressWidth > 0) {
			context.drawTexture(RenderLayer::getGuiTextured, HONEY_BAR_FG_TEXTURE,
				honeyBarX, honeyBarY,
				0, 0,
				progressWidth, HONEY_BAR_HEIGHT,
				HONEY_BG_TEX_W, HONEY_BG_TEX_H);
		}

		String titleStr = isHive ? "Beehive" : "Bee Nest";
		int titleX = guiLeft + (PNG_WIDTH - this.textRenderer.getWidth(titleStr)) / 2;
		context.drawText(this.textRenderer, titleStr, titleX, guiTop + 6, TITLE_COLOR, false);

		for (int i = 0; i < Math.min(beeEntities.size(), 3); i++) {
			BeeEntity bee = beeEntities.get(i);
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y;

			InventoryScreen.drawEntity(
				context,
				sx, sy - BEE_Y_OFFSET,
				sx + SLOT_SIZE, sy + SLOT_SIZE,
				BEE_RENDER_SIZE, 1.0f,
				(float) mouseX, (float) mouseY,
				bee
			);

			BeeGuiPacket.BeeInfo info = bees.get(i);
			float ratio = info.maxHealth() > 0 ? info.health() / info.maxHealth() : 1f;
			if (ratio > 0f && ratio < 1f) {
				drawHealthBar(context, sx, sy, info.health(), info.maxHealth());
			}
		}

		if (hoveredSlot >= 0 && hoveredSlot < bees.size()) {
			renderBeeTooltip(context, bees.get(hoveredSlot), mouseX, mouseY);
		}
		if (honeyBarHovered) {
			renderHoneyTooltip(context, mouseX, mouseY);
		}
	}

	private void drawHealthBar(DrawContext context, int slotX, int slotY, float health, float maxHealth) {
		int step = (int) Math.ceil((health / maxHealth) * HEALTH_BAR_COLORS.length);
		step = Math.max(1, Math.min(step, HEALTH_BAR_COLORS.length));
		int color = HEALTH_BAR_COLORS[step - 1];
		int barX = slotX + (SLOT_SIZE - HEALTH_BAR_WIDTH)  / 2;
		int barY = slotY + (SLOT_SIZE - HEALTH_BAR_HEIGHT) / 2 + 10;
		context.fill(barX,        barY, barX + step,             barY + 1, color);
		context.fill(barX + step, barY, barX + HEALTH_BAR_WIDTH, barY + 1, HEALTH_BAR_SHADOW);
		context.fill(barX, barY + 1,   barX + HEALTH_BAR_WIDTH, barY + 2, HEALTH_BAR_SHADOW);
	}

	private void renderBeeTooltip(DrawContext context, BeeGuiPacket.BeeInfo bee, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(bee.name() + (bee.hasNectar() ? " (with Nectar)" : "")));
		lines.add(Text.literal("HP: " + Math.round(bee.health()) + "/" + Math.round(bee.maxHealth()))
			.styled(s -> s.withColor(0x545454).withShadowColor(0xFF151515)));
		context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
	}

	private void renderHoneyTooltip(DrawContext context, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal("Honey"));
		lines.add(Text.literal("Level: " + honeyLevel + "/5")
			.styled(s -> s.withColor(0x545454).withShadowColor(0xFF151515)));
		context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
	}
}