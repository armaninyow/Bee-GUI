package com.armaninyow.beegui.client;

import com.armaninyow.beegui.BeeGUI;
import com.armaninyow.beegui.BeeGuiScreenTracker;
import com.armaninyow.beegui.network.BeeGuiPacket;
import com.armaninyow.beegui.network.BeeGuiRefreshPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class BeeManagementScreen extends Screen {

    private static final Identifier CONTAINER_TEXTURE =
        Identifier.fromNamespaceAndPath(BeeGUI.MOD_ID, "textures/gui/container.png");
    private static final Identifier HONEY_BAR_BG_TEXTURE =
        Identifier.fromNamespaceAndPath(BeeGUI.MOD_ID, "textures/gui/honey_bar_background.png");
    private static final Identifier HONEY_BAR_FG_TEXTURE =
        Identifier.fromNamespaceAndPath(BeeGUI.MOD_ID, "textures/gui/honey_bar_progress.png");

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

    private final List<Bee> beeEntities = new ArrayList<>();

    private static BeeManagementScreen currentScreen;

    public static BeeManagementScreen getCurrentScreen() {
        return currentScreen;
    }

    public BeeManagementScreen(boolean isHive, int honeyLevel, List<BeeGuiPacket.BeeInfo> bees, BlockPos pos) {
        super(Component.empty());
        this.isHive     = isHive;
        this.honeyLevel = honeyLevel;
        this.bees       = bees;
        this.pos        = pos;
        rebuildEntities();
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
        BeeGuiScreenTracker.openScreenPositions.add(pos);
        currentScreen = this;
    }

    @Override
    public void removed() {
        super.removed();
        BeeGuiScreenTracker.openScreenPositions.remove(pos);
        if (currentScreen == this) {
            currentScreen = null;
        }
    }

    private void rebuildEntities() {
        beeEntities.clear();
        Level world = Minecraft.getInstance().level;
        if (world == null) return;
        int previewId = -1;
        for (BeeGuiPacket.BeeInfo info : bees) {
            Bee entity = EntityTypes.BEE.create(world, EntitySpawnReason.LOAD);
            if (entity == null) continue;
            entity.setId(previewId--);
            if (info.isBaby())    entity.setAge(-24000);
            if (info.hasNectar()) entity.setHasNectar(true);
            beeEntities.add(entity);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isInGameUi() { return true; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0x80000000, 0x80000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
            ClientPlayNetworking.send(new BeeGuiRefreshPacket(pos));
            lastRefreshTime = now;
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

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

        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE,
            guiLeft, guiTop,
            0f, 0f,
            PNG_WIDTH, PNG_HEIGHT,
            PNG_WIDTH, PNG_HEIGHT);

        graphics.blit(RenderPipelines.GUI_TEXTURED, HONEY_BAR_BG_TEXTURE,
            honeyBarX, honeyBarY,
            0f, 0f,
            HONEY_BAR_WIDTH, HONEY_BAR_HEIGHT,
            HONEY_BG_TEX_W, HONEY_BG_TEX_H);

        int progressWidth = HONEY_SEGMENT_WIDTHS[Math.max(0, Math.min(honeyLevel, 5))];
        if (progressWidth > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, HONEY_BAR_FG_TEXTURE,
                honeyBarX, honeyBarY,
                0f, 0f,
                progressWidth, HONEY_BAR_HEIGHT,
                HONEY_BG_TEX_W, HONEY_BG_TEX_H);
        }

        String titleStr = isHive ? "Beehive" : "Bee Nest";
        int titleX = guiLeft + (PNG_WIDTH - this.font.width(titleStr)) / 2;
        graphics.text(this.font, titleStr, titleX, guiTop + 6, TITLE_COLOR, false);

        for (int i = 0; i < Math.min(beeEntities.size(), 3); i++) {
            Bee bee = beeEntities.get(i);
            int sx = guiLeft + SLOT_X[i];
            int sy = guiTop  + SLOT_Y;

            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                sx, sy,
                sx + SLOT_SIZE, sy + SLOT_SIZE,
                BEE_RENDER_SIZE, 0.1f,
                (float) mouseX, (float) mouseY,
                bee
            );

            BeeGuiPacket.BeeInfo info = bees.get(i);
            float ratio = info.maxHealth() > 0 ? info.health() / info.maxHealth() : 1f;
            if (ratio > 0f && ratio < 1f) {
                drawHealthBar(graphics, sx, sy, info.health(), info.maxHealth());
            }
        }

        if (hoveredSlot >= 0 && hoveredSlot < bees.size()) {
            renderBeeTooltip(graphics, bees.get(hoveredSlot), mouseX, mouseY);
        }
        if (honeyBarHovered) {
            renderHoneyTooltip(graphics, mouseX, mouseY);
        }
    }

    private void drawHealthBar(GuiGraphicsExtractor graphics, int slotX, int slotY, float health, float maxHealth) {
        int step = (int) Math.ceil((health / maxHealth) * HEALTH_BAR_COLORS.length);
        step = Math.max(1, Math.min(step, HEALTH_BAR_COLORS.length));
        int color = HEALTH_BAR_COLORS[step - 1];
        int barX = slotX + (SLOT_SIZE - HEALTH_BAR_WIDTH)  / 2;
        int barY = slotY + (SLOT_SIZE - HEALTH_BAR_HEIGHT) / 2 + 10;
        graphics.fill(barX,        barY, barX + step,             barY + 1, color);
        graphics.fill(barX + step, barY, barX + HEALTH_BAR_WIDTH, barY + 1, HEALTH_BAR_SHADOW);
        graphics.fill(barX, barY + 1,   barX + HEALTH_BAR_WIDTH, barY + 2, HEALTH_BAR_SHADOW);
    }

    private void renderBeeTooltip(GuiGraphicsExtractor graphics, BeeGuiPacket.BeeInfo bee, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(bee.name() + (bee.hasNectar() ? " (with Nectar)" : "")));
        lines.add(Component.literal("HP: " + Math.round(bee.health()) + "/" + Math.round(bee.maxHealth()))
            .withStyle(s -> s.withColor(0x545454)));
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private void renderHoneyTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Honey"));
        lines.add(Component.literal("Level: " + honeyLevel + "/5")
            .withStyle(s -> s.withColor(0x545454)));
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }
}