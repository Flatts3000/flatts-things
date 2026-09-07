package com.flatts.flattsthings.content.woodcutter;

import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTMenus;
import com.flatts.flattsthings.registry.FTRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * The woodcutter's menu: planks in one slot, a shape out of the other.
 *
 * <p><b>The options are real slots, and that is the whole design.</b> Vanilla's stonecutter reads
 * {@code level.recipeAccess().stonecutterRecipes()} - a prebuilt list the server syncs to the client
 * so the screen can draw the buttons. That is not extensible: {@code RecipeAccess} declares exactly
 * one {@code stonecutterRecipes()}, and {@code RecipeManager}'s property sets are a fixed
 * {@code Map.of}. A custom cutter therefore has no way to reuse it.
 *
 * <p>Rather than build a second recipe-syncing mechanism, the possible results live in a container of
 * their own and are added as slots. <b>Menus already sync their slot contents</b>, so the client
 * learns the options for free and the screen just draws them. It is fewer moving parts than a payload
 * of our own, and it cannot drift out of step with the server the way a separately-sent list could.
 *
 * <p>Those slots refuse to be picked up or placed into, so they are display surfaces rather than
 * storage. The screen turns a click on one into {@code clickMenuButton}, which is the same route
 * vanilla's cutter uses.
 *
 * <p><b>The recipe lookup is server-only.</b> A client has no full recipe manager in 26.1, so
 * {@code refreshOptions} runs behind an {@code isClientSide} guard and the client relies entirely on
 * the synced slots. Calling it on the client would find nothing and blank the list.
 */
public class WoodcutterMenu extends AbstractContainerMenu {

    /**
     * How many cuts one input may offer.
     *
     * <p>Everything this mod ships offers two - a stair and a slab - so this is headroom for a data
     * pack, not a number anything needs today. It is a fixed count because the slots have to exist
     * before the recipes are known: a menu's slot list is built in the constructor and is the thing
     * being synced.
     */
    public static final int MAX_OPTIONS = 8;

    private static final int INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 1;
    private static final int FIRST_OPTION_SLOT = 2;
    private static final int FIRST_PLAYER_SLOT = FIRST_OPTION_SLOT + MAX_OPTIONS;
    private static final int AFTER_PLAYER_SLOT = FIRST_PLAYER_SLOT + 36;

    private final ContainerLevelAccess access;
    private final Level level;
    private final DataSlot selected = DataSlot.standalone();
    private final ResultContainer resultContainer = new ResultContainer();
    private final SimpleContainer optionsContainer = new SimpleContainer(MAX_OPTIONS);
    private final List<RecipeHolder<WoodCuttingRecipe>> options = new ArrayList<>();

    private ItemStack lastInput = ItemStack.EMPTY;
    private long lastSoundTime;

    private final Slot inputSlot;
    private final Slot resultSlot;

    public final Container inputContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            WoodcutterMenu.this.slotsChanged(this);
        }
    };

    public WoodcutterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public WoodcutterMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(FTMenus.WOODCUTTER.get(), containerId);
        this.access = access;
        this.level = inventory.player.level();

        this.inputSlot = this.addSlot(new Slot(this.inputContainer, 0, 20, 33));
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                carried.onCraftedBy(player, carried.getCount());
                WoodcutterMenu.this.resultContainer.awardUsedRecipes(player,
                    List.of(WoodcutterMenu.this.inputSlot.getItem()));
                // ONE PLANK PER CUT, taken as the result leaves rather than when it is selected -
                // otherwise looking at an option would cost material.
                ItemStack remaining = WoodcutterMenu.this.inputSlot.remove(1);
                if (!remaining.isEmpty()) {
                    WoodcutterMenu.this.setupResult(WoodcutterMenu.this.selected.get());
                }
                access.execute((level, pos) -> {
                    long now = level.getGameTime();
                    if (WoodcutterMenu.this.lastSoundTime != now) {
                        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                        WoodcutterMenu.this.lastSoundTime = now;
                    }
                });
                super.onTake(player, carried);
            }
        });

        for (int index = 0; index < MAX_OPTIONS; index++) {
            // DISPLAY ONLY. Positioned where the screen draws the buttons so that a click lands on
            // something, but refusing both directions so nothing can be taken out or put in.
            this.addSlot(new Slot(this.optionsContainer, index, -2000, -2000) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlot(this.selected);
    }

    public int selectedIndex() {
        return this.selected.get();
    }

    /** What the client draws: the results this input can be cut into, in order. */
    public List<ItemStack> visibleOptions() {
        List<ItemStack> visible = new ArrayList<>();
        for (int index = 0; index < MAX_OPTIONS; index++) {
            ItemStack option = this.optionsContainer.getItem(index);
            if (!option.isEmpty()) {
                visible.add(option);
            }
        }
        return visible;
    }

    public boolean hasInput() {
        return this.inputSlot.hasItem() && !this.visibleOptions().isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, FTBlocks.WOODCUTTER.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < 0 || buttonId >= MAX_OPTIONS) {
            return false;
        }
        if (this.optionsContainer.getItem(buttonId).isEmpty()) {
            return false;
        }
        this.selected.set(buttonId);
        this.setupResult(buttonId);
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack input = this.inputSlot.getItem();
        if (!input.is(this.lastInput.getItem())) {
            this.lastInput = input.copy();
            this.refreshOptions(input);
        }
    }

    private void refreshOptions(ItemStack input) {
        this.selected.set(-1);
        this.resultSlot.set(ItemStack.EMPTY);
        this.options.clear();
        for (int index = 0; index < MAX_OPTIONS; index++) {
            this.optionsContainer.setItem(index, ItemStack.EMPTY);
        }

        if (this.level.isClientSide() || input.isEmpty()) {
            this.broadcastChanges();
            return;
        }

        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        List<RecipeHolder<WoodCuttingRecipe>> found = this.level.getServer().getRecipeManager()
            .recipeMap().getRecipesFor(FTRecipes.WOOD_CUTTING_TYPE.get(), recipeInput, this.level)
            .limit(MAX_OPTIONS)
            .toList();

        for (int index = 0; index < found.size(); index++) {
            RecipeHolder<WoodCuttingRecipe> holder = found.get(index);
            this.options.add(holder);
            this.optionsContainer.setItem(index, holder.value().assemble(recipeInput));
        }
        this.broadcastChanges();
    }

    private void setupResult(int index) {
        if (index >= 0 && index < this.options.size()) {
            RecipeHolder<WoodCuttingRecipe> holder = this.options.get(index);
            this.resultContainer.setRecipeUsed(holder);
            this.resultSlot.set(holder.value()
                .assemble(new SingleRecipeInput(this.inputContainer.getItem(0))));
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
            this.resultContainer.setRecipeUsed(null);
        }
        this.broadcastChanges();
    }

    @Override
    public MenuType<?> getType() {
        return FTMenus.WOODCUTTER.get();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.resultContainer
            && target.container != this.optionsContainer
            && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (slotIndex == RESULT_SLOT) {
            stack.getItem().onCraftedBy(stack, player);
            if (!this.moveItemStackTo(stack, FIRST_PLAYER_SLOT, AFTER_PLAYER_SLOT, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, moved);
        } else if (slotIndex == INPUT_SLOT) {
            if (!this.moveItemStackTo(stack, FIRST_PLAYER_SLOT, AFTER_PLAYER_SLOT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex >= FIRST_PLAYER_SLOT) {
            // INTO THE INPUT ONLY IF IT CAN BE CUT. Sending anything else there would make the
            // woodcutter a one-slot chest, and the player would have to take it back out by hand.
            if (this.canBeCut(stack)) {
                if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex < FIRST_PLAYER_SLOT + 27) {
                if (!this.moveItemStackTo(stack, FIRST_PLAYER_SLOT + 27, AFTER_PLAYER_SLOT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, FIRST_PLAYER_SLOT, FIRST_PLAYER_SLOT + 27,
                    false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // An option slot. Nothing to move: it is a picture of a result, not a stack.
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (slotIndex == RESULT_SLOT) {
            // WHATEVER WOULD NOT FIT GOES ON THE FLOOR, and leaving this out deletes it. Vanilla's
            // cutter has the same line and it is easy to read as belt-and-braces; it is not.
            // moveItemStackTo returns true after merging even ONE item, so a nearly full inventory
            // leaves a remainder in `stack` - and onTake above has already charged a plank and told
            // setupResult to overwrite the result slot's reference to it. The remainder then belongs
            // to nothing and is silently gone, with the player charged for it.
            player.drop(stack, false);
        }
        this.broadcastChanges();
        return moved;
    }

    private boolean canBeCut(ItemStack stack) {
        if (this.level.isClientSide()) {
            return true;
        }
        return this.level.getServer().getRecipeManager().recipeMap()
            .getRecipesFor(FTRecipes.WOOD_CUTTING_TYPE.get(), new SingleRecipeInput(stack),
                this.level)
            .findAny().isPresent();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(1);
        this.access.execute((level, pos) -> this.clearContainer(player, this.inputContainer));
    }
}
