package ryzlar.glass.client.tome;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ryzlar.glass.client.gui.holo.Holo;
import ryzlar.glass.client.gui.holo.HoloField;
import ryzlar.glass.client.gui.holo.HoloList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The GLASS Tome: the index (chapters and pages) and a search on the left, the page on the right: its 3D scene (drag to
 * turn it; pause and step it), what happens in it, the text (with links to other pages) and the recipes.
 */
public class TomeScreen extends Screen
{
    private static final Pattern LINK = Pattern.compile("\\[\\[([^|\\]]+)\\|([^\\]]+)]]");
    private static final int INDEX_W = 132;
    private static final int SLOT = 18;

    /** The page last read, opened again next time. */
    @Nullable
    private static String lastPage;

    private final TomeBook book = TomeBook.get();
    @Nullable
    private TomeBook.Page page;
    private HoloList<Entry> index;
    private HoloField search;

    private int left, top, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private double scroll;
    private int contentHeight;
    private final List<Link> links = new ArrayList<>();

    //The scene: its time, whether it plays, and how it is turned.
    private float sceneTime;
    private boolean playing = true;
    private long lastFrame = Util.getMillis();
    private float yaw = -35F, pitch = 28F;
    private boolean dragging;
    private int sceneX, sceneY, sceneW, sceneH;
    private int controlsY;
    private float openedAt = -1F;

    /** A row of the index: a chapter's title, or a page. */
    private record Entry(@Nullable TomeBook.Chapter chapter, @Nullable TomeBook.Page page) {}

    private record Link(int x0, int y0, int x1, int y1, String page) {}

    public TomeScreen(@Nullable String pageId)
    {
        super(Component.translatable("item.generallaymansaestheticspyingscreen.glass_tome"));
        String id = pageId != null ? pageId : lastPage;
        page = id != null && book.page(id) != null ? book.page(id) : book.first();
    }

    @Nullable
    public TomeBook.Page page()
    {
        return page;
    }

    @Override
    protected void init()
    {
        panelW = Math.min(width - 16, 500);
        panelH = Math.min(height - 16, 320);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        if(openedAt < 0F)
        {
            openedAt = Holo.seconds();
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ryzlar.glass.GlassSounds.UI_OPEN, 1F, 0.8F));
        }
        String query = search != null ? search.getValue() : "";
        search = new HoloField(font, left + 8, top + 38, INDEX_W, 16, Component.translatable("glass.tome.search"));
        search.setHint(Component.translatable("glass.tome.search").withColor(Holo.MUTED));
        search.setValue(query);
        search.setResponder(text -> fillIndex());
        addRenderableWidget(search);
        index = new HoloList<>(left + 8, top + 58, INDEX_W, panelH - 66, this::label, null, Component.translatable("glass.tome.noResults"));
        index.onSelect(entry -> {
            TomeBook.Page target = entry.page() != null ? entry.page() : entry.chapter() != null && !entry.chapter().pages().isEmpty()
                    ? entry.chapter().pages().getFirst() : null;
            if(target != null)
            {
                open(target);
            }
        });
        addRenderableWidget(index);
        contentX = left + INDEX_W + 18;
        contentY = top + 38;
        contentW = left + panelW - 10 - contentX;
        contentH = panelH - 46;
        fillIndex();
    }

    private Component label(Entry entry)
    {
        if(entry.chapter() != null)
        {
            return Component.literal(entry.chapter().title().toUpperCase(java.util.Locale.ROOT)).withColor(Holo.ACCENT);
        }
        return Component.literal("  " + entry.page().title());
    }

    private void fillIndex()
    {
        List<Entry> entries = new ArrayList<>();
        String query = search.getValue().trim();
        if(query.isEmpty())
        {
            for(TomeBook.Chapter chapter : book.chapters)
            {
                entries.add(new Entry(chapter, null));
                for(TomeBook.Page p : chapter.pages())
                {
                    entries.add(new Entry(null, p));
                }
            }
        }
        else
        {
            for(TomeBook.Page p : book.search(query))
            {
                entries.add(new Entry(null, p));
            }
        }
        index.setItems(entries);
        index.selectWhere(e -> e.page() != null && e.page() == page);
        index.ensureVisible(index.getSelectedIndex());
    }

    /** Shows a page (from the index, a link or the look-up key). */
    public void open(TomeBook.Page target)
    {
        if(target != page)
        {
            page = target;
            scroll = 0;
            sceneTime = 0F;
            playing = true;
            yaw = -35F;
            pitch = 28F;
        }
        lastPage = target.id();
        index.selectWhere(e -> e.page() != null && e.page() == target);
        index.ensureVisible(index.getSelectedIndex());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        g.fill(0, 0, width, height, 0xB0010509);
        Holo.panel(g, left, top, panelW, panelH, (Holo.seconds() - openedAt) / 0.18F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        long now = Util.getMillis();
        if(playing)
        {
            sceneTime += (now - lastFrame) / 1000F;
        }
        lastFrame = now;
        super.render(g, mouseX, mouseY, partialTick);
        Component subtitle = page == null ? Component.empty() : Component.literal(page.chapter()).withColor(Holo.MUTED);
        Holo.header(g, font, left, top, panelW, Component.translatable("glass.tome.title"), subtitle);
        if(page == null)
        {
            return;
        }
        Holo.inset(g, contentX - 4, contentY - 2, contentW + 8, contentH + 4);
        g.enableScissor(contentX - 3, contentY - 1, contentX + contentW + 3, contentY + contentH + 1);
        links.clear();
        int y = contentY + 4 - (int)scroll;
        g.drawString(font, Component.literal(page.title()).withColor(Holo.TEXT), contentX, y, Holo.TEXT, false);
        g.fill(contentX, y + 11, contentX + Math.min(contentW, font.width(page.title()) + 12), y + 12, Holo.ACCENT);
        y += 18;
        TomeScenes.Scene scene = page.scene() == null ? null : TomeScenes.get(page.scene());
        if(scene != null)
        {
            y = drawScene(g, scene, y, mouseX, mouseY);
        }
        ItemStack hovered = ItemStack.EMPTY;
        for(TomeBook.Block block : page.body())
        {
            if(block instanceof TomeBook.Text text)
            {
                y = drawText(g, text.text(), y, mouseX, mouseY) + 6;
            }
            else if(block instanceof TomeBook.Recipe recipe)
            {
                TomeRecipes.Grid grid = TomeRecipes.of(recipe.item());
                if(grid != null)
                {
                    ItemStack h = drawRecipe(g, grid, y, mouseX, mouseY);
                    if(!h.isEmpty())
                    {
                        hovered = h;
                    }
                    y += SLOT * 3 + 18;
                }
            }
        }
        contentHeight = y + (int)scroll - contentY;
        g.disableScissor();
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - contentH));
        if(!hovered.isEmpty())
        {
            g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
    }

    private int drawScene(GuiGraphics g, TomeScenes.Scene scene, int y, int mouseX, int mouseY)
    {
        sceneW = contentW;
        //Room left under it for the controls and a caption of up to three lines, with the title above.
        sceneH = Mth.clamp(Math.min(contentW * 11 / 20, contentH - 60), 80, 150);
        sceneX = contentX;
        sceneY = y;
        g.fill(sceneX, sceneY, sceneX + sceneW, sceneY + sceneH, 0xFF0A1A24);
        g.fillGradient(sceneX, sceneY, sceneX + sceneW, sceneY + sceneH, 0x30416E8C, 0x0003080C);
        Holo.frame(g, sceneX, sceneY, sceneW, sceneH, Holo.ACCENT_DIM);
        float scale = Math.min(sceneW, sceneH * 1.6F) / (scene.size() * 1.45F);
        g.guiRenderState.submitPicturesInPictureState(new TomeSceneRenderer.State(page.scene(), sceneTime, yaw, pitch,
                sceneX + 1, sceneY + 1, sceneX + sceneW - 1, sceneY + sceneH - 1, scale, g.scissorStack.peek()));
        Holo.brackets(g, sceneX, sceneY, sceneW, sceneH, 6, Holo.ACCENT);
        //Progress through the loop.
        float progress = (sceneTime % scene.length()) / scene.length();
        g.fill(sceneX + 1, sceneY + sceneH - 2, sceneX + 1 + (int)((sceneW - 2) * progress), sceneY + sceneH - 1, Holo.ACCENT);
        y += sceneH + 3;
        controlsY = y;
        control(g, contentX, y, Component.translatable("glass.tome.back"), mouseX, mouseY);
        control(g, contentX + 16, y, Component.translatable(playing ? "glass.tome.pause" : "glass.tome.play"), mouseX, mouseY);
        control(g, contentX + 32, y, Component.translatable("glass.tome.forward"), mouseX, mouseY);
        List<net.minecraft.util.FormattedCharSequence> caption = font.split(Component.literal(scene.caption(sceneTime % scene.length())), contentW - 54);
        for(int i = 0; i < Math.min(3, caption.size()); i++)
        {
            g.drawString(font, caption.get(i), contentX + 52, y + 2 + i * 10, Holo.ACCENT, false);
        }
        if(mouseX >= sceneX && mouseX < sceneX + sceneW && mouseY >= sceneY && mouseY < sceneY + sceneH && !dragging)
        {
            g.drawString(font, Component.translatable("glass.tome.drag"), sceneX + 5, sceneY + 4, Holo.withAlpha(Holo.MUTED, 0.8F), false);
        }
        return y + 34;
    }

    private void control(GuiGraphics g, int x, int y, Component symbol, int mouseX, int mouseY)
    {
        boolean hover = mouseX >= x && mouseX < x + 14 && mouseY >= y && mouseY < y + 13;
        g.fill(x, y, x + 14, y + 13, hover ? 0x553CE6FF : 0xCC07141C);
        Holo.frame(g, x, y, 14, 13, hover ? Holo.ACCENT : Holo.ACCENT_DIM);
        g.drawCenteredString(font, symbol, x + 7, y + 3, Holo.TEXT);
    }

    /** Draws a paragraph wrapped to the page, links in the accent colour (and clickable). Returns the y below it. */
    private int drawText(GuiGraphics g, String text, int y, int mouseX, int mouseY)
    {
        //Words, each with the page it links to (or null).
        List<String[]> words = new ArrayList<>();
        Matcher matcher = LINK.matcher(text);
        int at = 0;
        while(matcher.find())
        {
            for(String word : text.substring(at, matcher.start()).split(" "))
            {
                words.add(new String[] { word, null });
            }
            for(String word : matcher.group(2).split(" "))
            {
                words.add(new String[] { word, matcher.group(1) });
            }
            at = matcher.end();
        }
        for(String word : text.substring(at).split(" "))
        {
            words.add(new String[] { word, null });
        }
        int x = contentX;
        int space = font.width(" ");
        for(String[] word : words)
        {
            if(word[0].isEmpty())
            {
                continue;
            }
            int w = font.width(word[0]);
            if(x > contentX && x + w > contentX + contentW)
            {
                x = contentX;
                y += 11;
            }
            boolean link = word[1] != null;
            boolean hover = link && mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 10;
            g.drawString(font, word[0], x, y, link ? (hover ? 0xFFFFFFFF : Holo.ACCENT) : Holo.TEXT, false);
            if(link)
            {
                g.fill(x, y + 9, x + w, y + 10, Holo.withAlpha(Holo.ACCENT, hover ? 1F : 0.5F));
                links.add(new Link(x, y - 1, x + w + space, y + 10, word[1]));
            }
            x += w + space;
        }
        return y + 11;
    }

    /** A 3x3 crafting grid and its result. Returns the stack under the mouse, if any. */
    private ItemStack drawRecipe(GuiGraphics g, TomeRecipes.Grid grid, int y, int mouseX, int mouseY)
    {
        g.drawString(font, Component.translatable("glass.tome.recipe").append(grid.shapeless() ? " (any shape)" : ""), contentX, y, Holo.MUTED, false);
        y += 11;
        ItemStack hovered = ItemStack.EMPTY;
        int cycle = (int)(Util.getMillis() / 1000L);
        for(int i = 0; i < 9; i++)
        {
            int sx = contentX + (i % 3) * SLOT, sy = y + (i / 3) * SLOT;
            g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0xCC02080D);
            Holo.frame(g, sx, sy, SLOT - 1, SLOT - 1, 0xFF123F48);
            List<ItemStack> options = grid.slots().get(i);
            if(!options.isEmpty())
            {
                ItemStack stack = options.get(cycle % options.size());
                g.renderItem(stack, sx, sy);
                if(mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT)
                {
                    hovered = stack;
                }
            }
        }
        int ax = contentX + SLOT * 3 + 6, ay = y + SLOT + 4;
        g.drawString(font, "▶", ax, ay, Holo.ACCENT, false);
        int rx = ax + 14, ry = y + SLOT - 4;
        g.fill(rx - 2, ry - 2, rx + SLOT + 1, ry + SLOT + 1, 0xCC02080D);
        Holo.frame(g, rx - 2, ry - 2, SLOT + 3, SLOT + 3, Holo.ACCENT);
        g.renderItem(grid.result(), rx, ry);
        g.renderItemDecorations(font, grid.result(), rx, ry);
        if(mouseX >= rx && mouseX < rx + SLOT && mouseY >= ry && mouseY < ry + SLOT)
        {
            hovered = grid.result();
        }
        return hovered;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(page != null && mouseX >= contentX && mouseX < contentX + contentW && mouseY >= contentY && mouseY < contentY + contentH)
        {
            for(Link link : links)
            {
                if(mouseX >= link.x0() && mouseX < link.x1() && mouseY >= link.y0() && mouseY < link.y1())
                {
                    TomeBook.Page target = book.page(link.page());
                    if(target != null)
                    {
                        open(target);
                        return true;
                    }
                }
            }
            TomeScenes.Scene scene = page.scene() == null ? null : TomeScenes.get(page.scene());
            if(scene != null && mouseY >= controlsY && mouseY < controlsY + 13)
            {
                if(mouseX >= contentX && mouseX < contentX + 14)
                {
                    sceneTime = Math.max(0F, sceneTime - 1F);
                    return true;
                }
                if(mouseX >= contentX + 16 && mouseX < contentX + 30)
                {
                    playing = !playing;
                    return true;
                }
                if(mouseX >= contentX + 32 && mouseX < contentX + 46)
                {
                    sceneTime += 1F;
                    return true;
                }
            }
            if(scene != null && mouseX >= sceneX && mouseX < sceneX + sceneW && mouseY >= sceneY && mouseY < sceneY + sceneH)
            {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if(dragging)
        {
            yaw += (float)dragX * 1.2F;
            pitch = Mth.clamp(pitch + (float)dragY * 1.2F, -10F, 89F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)
    {
        if(mouseX >= contentX && mouseX < contentX + contentW && mouseY >= contentY && mouseY < contentY + contentH)
        {
            scroll = Mth.clamp(scroll - vertical * 22, 0, Math.max(0, contentHeight - contentH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** Tests: scroll the page. */
    public void scrollTo(double value)
    {
        scroll = value;
    }

    /** Tests: show the scene at a moment, from a side. */
    public void sceneAt(float time, float sceneYaw, float scenePitch)
    {
        sceneTime = time;
        playing = false;
        yaw = sceneYaw;
        pitch = scenePitch;
    }
}
