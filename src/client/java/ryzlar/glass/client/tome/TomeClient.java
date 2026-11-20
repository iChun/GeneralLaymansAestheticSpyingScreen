package ryzlar.glass.client.tome;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import ryzlar.glass.Glass;
import ryzlar.glass.GlassRegistry;
import ryzlar.glass.client.mixin.AbstractContainerScreenAccessor;

/** Sets up the GLASS Tome on the client: its scene renderer, the look-up key, its tooltip, and reloading its pages. */
public final class TomeClient
{
    /** Over a GLASS item in an inventory: open its page in the tome. */
    public static final KeyMapping LOOK_UP = new KeyMapping("key.generallaymansaestheticspyingscreen.tome", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.generallaymansaestheticspyingscreen");

    private TomeClient() {}

    public static void init()
    {
        SpecialGuiElementRegistry.register(context -> new TomeSceneRenderer(context.vertexConsumers()));
        KeyBindingHelper.registerKeyBinding(LOOK_UP);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if(screen instanceof AbstractContainerScreen<?>)
            {
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, modifiers) -> {
                    if(LOOK_UP.matches(key, scancode) && !(s.getFocused() instanceof EditBox))
                    {
                        Slot slot = ((AbstractContainerScreenAccessor)s).glass$getHoveredSlot();
                        if(slot != null && slot.hasItem())
                        {
                            lookUp(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()));
                        }
                    }
                });
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if(stack.is(GlassRegistry.GLASS_TOME))
            {
                lines.add(Component.translatable("glass.tome.tooltip").withStyle(ChatFormatting.GRAY));
            }
            else if(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Glass.MOD_ID) && TomeBook.get().pageFor(BuiltInRegistries.ITEM.getKey(stack.getItem())) != null)
            {
                lines.add(Component.translatable("glass.tome.lookUp", LOOK_UP.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_AQUA));
            }
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener()
        {
            @Override
            public ResourceLocation getFabricId()
            {
                return Glass.id("tome");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager)
            {
                TomeBook.reset();
                TomeRecipes.reset();
            }
        });
    }

    /** Opens the tome at the page about this item. Returns false if there is none. */
    public static boolean lookUp(ResourceLocation item)
    {
        TomeBook.Page page = TomeBook.get().pageFor(item);
        if(page == null)
        {
            return false;
        }
        open(page.id());
        return true;
    }

    public static void open(@Nullable String page)
    {
        Minecraft.getInstance().setScreen(new TomeScreen(page));
    }
}
