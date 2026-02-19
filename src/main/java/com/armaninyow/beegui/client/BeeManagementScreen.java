package com.armaninyow.beegui.client;

import com.armaninyow.beegui.BeeGUI;
import com.armaninyow.beegui.network.BeeGuiPacket;
import com.armaninyow.beegui.network.BeeGuiRefreshPacket;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.ArrayList;

@Environment(EnvType.CLIENT)
public class BeeManagementScreen extends Screen {

	// Static textures
	private static final Identifier CONTAINER_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/container.png");
	private static final Identifier HONEY_BAR_BG_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/honey_bar_background.png");
	private static final Identifier HONEY_BAR_FG_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/honey_bar_progress.png");

	// Bee textures (flat, no scale folders)
	private static final Identifier BEE_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/bee.png");
	private static final Identifier BABY_BEE_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/baby_bee.png");
	private static final Identifier BEE_NECTAR_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/bee_with_nectar.png");
	private static final Identifier BABY_BEE_NECTAR_TEXTURE =
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/baby_bee_with_nectar.png");

	// Durability bar textures: 01.png (near death) → 09.png (almost full health)
	// No texture at full health (ratio == 1.0) — bar is hidden.
	private static final Identifier[] DURABILITY_TEXTURES = new Identifier[]{
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/01.png"), // ratio 0.00–0.11
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/02.png"), // ratio 0.11–0.22
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/03.png"), // ratio 0.22–0.33
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/04.png"), // ratio 0.33–0.44
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/05.png"), // ratio 0.44–0.55
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/06.png"), // ratio 0.55–0.66
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/07.png"), // ratio 0.66–0.77
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/08.png"), // ratio 0.77–0.88
		Identifier.of(BeeGUI.MOD_ID, "textures/gui/durability/09.png"), // ratio 0.88–0.99
	};
	private static final int DURABILITY_TEX_SIZE = 16; // each durability png is 16×16

	// Container dimensions — must match container.png pixel size (1px = 1 GUI pixel)
	private static final int PNG_WIDTH  = 68;
	private static final int PNG_HEIGHT = 50;

	// Title color #3F3F3F fully opaque
	private static final int TITLE_COLOR = 0xFF3F3F3F;

	// Slot positions relative to GUI top-left (PNG pixels)
	private static final int[] SLOT_X  = {8, 26, 44};
	private static final int   SLOT_Y  = 17;
	private static final int   SLOT_SIZE = 16;

	// Native size of bee PNG files; stretched into SLOT_SIZE×SLOT_SIZE
	private static final int BEE_TEX_SIZE = 130;

	// Honey bar: X centered dynamically, Y fixed
	private static final int HONEY_BAR_Y      = 38;
	private static final int HONEY_BAR_WIDTH  = 46;
	private static final int HONEY_BAR_HEIGHT = 5;
	private static final int HONEY_BG_TEX_W   = 46;
	private static final int HONEY_BG_TEX_H   = 5;
	private static final int[] HONEY_SEGMENT_WIDTHS = {0, 10, 19, 28, 37, 46};

	// Auto-refresh: send a refresh packet to the server every REFRESH_INTERVAL_MS milliseconds
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

	public BeeManagementScreen(boolean isHive, int honeyLevel, List<BeeGuiPacket.BeeInfo> bees, BlockPos pos) {
		super(Text.empty());
		this.isHive     = isHive;
		this.honeyLevel = honeyLevel;
		this.bees       = bees;
		this.pos        = pos;
	}

	/** Called by BeeGUIClient when a refresh payload arrives for this pos. */
	public void updateData(boolean isHive, int honeyLevel, List<BeeGuiPacket.BeeInfo> bees) {
		this.isHive     = isHive;
		this.honeyLevel = honeyLevel;
		this.bees       = bees;
	}

	/** Exposed so BeeGUIClient can compare which hive this screen is for. */
	public BlockPos getPos() { return pos; }

	@Override
	protected void init() {
		super.init();
		this.guiLeft = (this.width  - PNG_WIDTH)  / 2;
		this.guiTop  = (this.height - PNG_HEIGHT) / 2;
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public boolean keyPressed(KeyInput input) {
		if (this.client != null && this.client.options.inventoryKey.matchesKey(input)) {
			this.close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Skip vanilla blur (applyBlur can only fire once per frame)
		context.fill(0, 0, this.width, this.height, 0x80000000);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Auto-refresh: request updated data from server every second
		long now = System.currentTimeMillis();
		if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
			ClientPlayNetworking.send(new BeeGuiRefreshPacket(pos));
			lastRefreshTime = now;
		}

		this.renderBackground(context, mouseX, mouseY, delta);

		// Hover detection
		hoveredSlot     = -1;
		honeyBarHovered = false;
		for (int i = 0; i < 3; i++) {
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y;
			if (mouseX >= sx && mouseX < sx + SLOT_SIZE
				&& mouseY >= sy && mouseY < sy + SLOT_SIZE) {
				hoveredSlot = i;
				break;
			}
		}
		int honeyBarX = guiLeft + (PNG_WIDTH - HONEY_BAR_WIDTH) / 2;
		int honeyBarY = guiTop  + HONEY_BAR_Y;
		if (mouseX >= honeyBarX && mouseX < honeyBarX + HONEY_BAR_WIDTH
			&& mouseY >= honeyBarY && mouseY < honeyBarY + HONEY_BAR_HEIGHT) {
			honeyBarHovered = true;
		}

		RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

		// Container background
		context.drawTexture(pipeline, CONTAINER_TEXTURE,
			guiLeft, guiTop,
			0f, 0f,
			PNG_WIDTH, PNG_HEIGHT,
			PNG_WIDTH, PNG_HEIGHT);

		// Honey bar background
		context.drawTexture(pipeline, HONEY_BAR_BG_TEXTURE,
			honeyBarX, honeyBarY,
			0f, 0f,
			HONEY_BAR_WIDTH, HONEY_BAR_HEIGHT,
			HONEY_BG_TEX_W, HONEY_BG_TEX_H);

		// Honey bar progress
		int progressWidth = HONEY_SEGMENT_WIDTHS[Math.max(0, Math.min(honeyLevel, 5))];
		if (progressWidth > 0) {
			context.drawTexture(pipeline, HONEY_BAR_FG_TEXTURE,
				honeyBarX, honeyBarY,
				0f, 0f,
				progressWidth, HONEY_BAR_HEIGHT,
				progressWidth, HONEY_BAR_HEIGHT,
				HONEY_BG_TEX_W, HONEY_BG_TEX_H);
		}

		// Title centered horizontally
		String titleStr = isHive ? "Beehive" : "Bee Nest";
		int titleX = guiLeft + (PNG_WIDTH - this.textRenderer.getWidth(titleStr)) / 2;
		context.drawText(this.textRenderer, titleStr,
			titleX,
			guiTop + 6, // <-- Y: vertical offset from container top edge
			TITLE_COLOR, false);

		// Bee icons: stretch BEE_TEX_SIZE source into SLOT_SIZE destination
		float beeScale = (float) SLOT_SIZE / BEE_TEX_SIZE;
		for (int i = 0; i < Math.min(bees.size(), 3); i++) {
			BeeGuiPacket.BeeInfo bee = bees.get(i);
			int sx = guiLeft + SLOT_X[i];
			int sy = guiTop  + SLOT_Y;

			context.getMatrices().pushMatrix();
			context.getMatrices().translate(sx, sy);
			context.getMatrices().scale(beeScale, beeScale);
			context.drawTexture(pipeline, getBeeTexture(bee),
				0, 0,
				0f, 0f,
				BEE_TEX_SIZE, BEE_TEX_SIZE,
				BEE_TEX_SIZE, BEE_TEX_SIZE);
			context.getMatrices().popMatrix();

			// Durability bar overlay — hidden at 0 HP (dead) and full health
			float ratio = bee.maxHealth() > 0 ? bee.health() / bee.maxHealth() : 1f;
			if (ratio > 0f && ratio < 1f) {
				drawDurabilityBar(context, pipeline, sx, sy, bee.health(), bee.maxHealth());
			}
		}

		super.render(context, mouseX, mouseY, delta);

		if (hoveredSlot >= 0 && hoveredSlot < bees.size()) {
			renderBeeTooltip(context, bees.get(hoveredSlot), mouseX, mouseY);
		}
		if (honeyBarHovered) {
			renderHoneyTooltip(context, mouseX, mouseY);
		}
	}

	/**
	 * Draws the durability bar PNG on top of the slot.
	 * Maps current HP to file: 1 HP → 01.png, 2 HP → 02.png, ... 9 HP → 09.png.
	 * 0 HP (dead) and full health → nothing drawn (handled by caller).
	 */
	private void drawDurabilityBar(DrawContext context, RenderPipeline pipeline, int slotX, int slotY, float health, float maxHealth) {
		int hp = Math.round(health);
		if (hp <= 0 || hp >= Math.round(maxHealth)) return;
		// Clamp to available textures (01–09)
		int index = Math.max(1, Math.min(hp, DURABILITY_TEXTURES.length));
		Identifier tex = DURABILITY_TEXTURES[index - 1];
		context.drawTexture(pipeline, tex,
			slotX, slotY,
			0f, 0f,
			DURABILITY_TEX_SIZE, DURABILITY_TEX_SIZE,
			DURABILITY_TEX_SIZE, DURABILITY_TEX_SIZE);
	}

	private Identifier getBeeTexture(BeeGuiPacket.BeeInfo bee) {
		if (bee.isBaby()) return bee.hasNectar() ? BABY_BEE_NECTAR_TEXTURE : BABY_BEE_TEXTURE;
		return bee.hasNectar() ? BEE_NECTAR_TEXTURE : BEE_TEXTURE;
	}

	private void renderBeeTooltip(DrawContext context, BeeGuiPacket.BeeInfo bee, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(bee.name() + (bee.hasNectar() ? " (with Nectar)" : "")));
		lines.add(Text.literal("HP: " + Math.round(bee.health()) + "/" + Math.round(bee.maxHealth())).styled(s -> s.withColor(0xA8A8A8)));
		context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
	}

	private void renderHoneyTooltip(DrawContext context, int mouseX, int mouseY) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal("Honey"));
		lines.add(Text.literal("Level: " + honeyLevel + "/5").styled(s -> s.withColor(0xA8A8A8)));
		context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
	}
}