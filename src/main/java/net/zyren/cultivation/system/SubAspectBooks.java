package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;

/**
 * Sub-aspect books and major-aspect tomes.
 *
 * The two ITEMS are made in MCreator (texture, name, stack size, creative tab), with exactly these
 * registry names:
 *   sub_aspect_book  -> teaches ONE sub-aspect, stored on the item: custom data {sub_aspect:"FIRE_VOLCANIC"}
 *   aspect_tome      -> teaches a RANDOM sub-aspect of one aspect:   custom data {aspect:"FIRE"}
 * Everything else (what it teaches, using it) is handled here, so one item covers every
 * sub-aspect / aspect. A blank one (e.g. from the creative tab) does nothing.
 *
 * Use = right-click. On success the item is used up (not in creative); on refusal it is kept.
 * How players obtain them is not decided yet: /qi subaspect book gives them for testing.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class SubAspectBooks {
	public static final ResourceLocation BOOK_ID = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "sub_aspect_book");
	public static final ResourceLocation TOME_ID = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "aspect_tome");
	private static final String KEY_SUB = "sub_aspect";
	private static final String KEY_ASPECT = "aspect";

	private SubAspectBooks() {
	}

	// ---------------------------------------------------------------- reading

	public static boolean isBook(ItemStack stack) {
		return !stack.isEmpty() && BOOK_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	public static boolean isTome(ItemStack stack) {
		return !stack.isEmpty() && TOME_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/** The sub-aspect a book teaches, or null (blank / not a book). */
	public static SubAspectType bookSub(ItemStack stack) {
		return isBook(stack) ? SubAspectType.fromId(tag(stack).getString(KEY_SUB)) : null;
	}

	/** The aspect a tome teaches from, or null (blank / not a tome / aspect without sub-aspects). */
	public static AspectType tomeAspect(ItemStack stack) {
		if (!isTome(stack))
			return null;
		AspectType a = AspectType.fromId(tag(stack).getString(KEY_ASPECT));
		return a != null && !SubAspectType.of(a).isEmpty() ? a : null;
	}

	private static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	// ---------------------------------------------------------------- making (commands, later loot)

	/** @return the book, or EMPTY if the sub_aspect_book item doesn't exist (not made in MCreator yet) */
	public static ItemStack makeBook(SubAspectType sub) {
		ItemStack stack = make(BOOK_ID);
		if (!stack.isEmpty()) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putString(KEY_SUB, sub.id()));
			stack.set(DataComponents.ITEM_NAME, Component.literal("Book of " + sub.fullName()));
		}
		return stack;
	}

	/** @return the tome, or EMPTY if the aspect_tome item doesn't exist (not made in MCreator yet) */
	public static ItemStack makeTome(AspectType aspect) {
		ItemStack stack = make(TOME_ID);
		if (!stack.isEmpty()) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putString(KEY_ASPECT, aspect.id()));
			stack.set(DataComponents.ITEM_NAME, Component.literal(aspect.displayName() + " Tome"));
		}
		return stack;
	}

	private static ItemStack make(ResourceLocation id) {
		Item item = BuiltInRegistries.ITEM.get(id);
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	// ---------------------------------------------------------------- using

	@SubscribeEvent
	public static void onUse(PlayerInteractEvent.RightClickItem event) {
		ItemStack stack = event.getItemStack();
		boolean book = isBook(stack), tome = !book && isTome(stack);
		if (!book && !tome)
			return;
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		if (!(event.getEntity() instanceof ServerPlayer player))
			return; // client side: just swing; the server decides

		ActionResult r = book ? SubAspectLearning.learn(player, bookSub(stack)) : SubAspectLearning.learnRandom(player, tomeAspect(stack));
		player.displayClientMessage(Component.literal(r.message()).withStyle(r.success() ? ChatFormatting.AQUA : ChatFormatting.RED), false);
		if (r.success()) {
			if (!player.getAbilities().instabuild)
				stack.shrink(1);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1f);
		}
	}
}
