package justfatlard.map_plus_plus.integration;

import java.util.List;
import java.util.function.Predicate;
import justfatlard.map_plus_plus.Main;
import justfatlard.map_plus_plus.MapPlusPlusPlayerAccess;
import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.map_plus_plus.inventory.MapSlot;
import justfatlard.village_quests.api.LessonApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * A cartographer who teaches what a map and a needle are actually for.
 *
 * <p>Registered with Village Quests when that mod is present. Everything about
 * pacing an apprenticeship is its business; what is here is the trade -- and
 * the trade is mostly this mod's, so it belongs in this mod. The vanilla half
 * underneath it, what each kind of compass points at and why all three go
 * blank across a dimension line for three different reasons, is the part the
 * game explains least.
 *
 * <p>The last lesson gains a paragraph when {@code dead-heads} is installed,
 * because the two mods together change what a recovery compass is for: a death
 * there leaves a head holding your things rather than a pile on a despawn
 * timer, so the bearing stops being a race and becomes a walk. Neither mod can
 * say that on its own.
 *
 * <p>This class must only be touched behind a mod-loaded check. It refers to
 * Village Quests types directly, so loading it without that mod present throws.
 */
public final class VillageQuestsLessons {
	private VillageQuestsLessons() {}

	private static final boolean DEAD_HEADS = FabricLoader.getInstance().isModLoaded("dead-heads");
	private static final int BLANK_MAPS_AT_THE_END = 3;

	public static void register() {
		LessonApi.register(new LessonApi.Craft(
			"map-plus-plus:cartography",
			"cartographer",
			LessonApi.Policy.standard(),
			lessons(),
			new LessonApi.Openings(
				LessonApi.lines(
					"{former} is gone. They had started you on something, and I would hate for it to stop where it stopped. ",
					"You were {former}'s, weren't you -- for the maps. I would not have stepped in while they were about. Since they aren't: ",
					"*sets down what they were doing* You're {former}'s student. I know roughly how far they got you. "),
				LessonApi.lines(
					"I've the next one for you, when you've a day free.",
					"*rolling something up* There's another when you want it. No rush -- half of it is walking anyway.",
					"Next one's ready. It'll keep."),
				LessonApi.lines(
					"{former} is gone. Their table is still by the window. Nobody has moved it.",
					"You'll have heard about {former}. They were showing you the slots, weren't they."),
				LessonApi.lines(
					"*notices where your map is* You keep it in the slot. Half the people through here still keep it in a hand.",
					"You've a needle on you and you're not looking at it. That means you already know which way it points.",
					"Somebody walked you through the slots properly. {mentor}? Thought so -- they always start with the map.")),
			new LessonApi.Hooks() {
				/**
				 * A cartographer's lessons are examinations, not purchases. The
				 * first thing taught is where to keep the map; confiscating it
				 * at the counter would undo the lesson and take the minimap
				 * with it.
				 */
				@Override
				public boolean takesTheWork() {
					return false;
				}

				/**
				 * The dedicated slots are a container of their own, so a map
				 * equipped exactly as lesson one instructs is not in the main
				 * inventory. Without this the better student is the one who
				 * fails.
				 */
				@Override
				public boolean holdsElsewhere(ServerPlayer player, Predicate<ItemStack> wanted) {
					if (!(player instanceof MapPlusPlusPlayerAccess access)) return false;

					MapPlusPlusInventory equipped = access.mapPlusPlus$getInventory();
					for (int slot = 0; slot < equipped.getContainerSize(); slot++) {
						ItemStack stack = equipped.getItem(slot);
						if (!stack.isEmpty() && wanted.test(stack)) return true;
					}
					return false;
				}

				@Override
				public void onGraduate(ServerPlayer player, ServerLevel world, LessonApi.Teacher teacher) {
					teacher.give(new ItemStack(Items.MAP, BLANK_MAPS_AT_THE_END));
					teacher.says("Take the blank ones. You'll want somewhere to put what you find next.");
					teacher.laterInTheVillage("Someone stood at the edge of the village with a map held out at arm's length, "
						+ "turning slowly, trying to make the drawing agree with the hills.", 0);
				}
			}));
	}

	private static boolean hasMobSight(ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null) return false;
		for (Holder<Enchantment> held : enchantments.keySet()) {
			if (held.is(Main.MOB_SIGHT)) return true;
		}
		return false;
	}

	private static boolean isBoundCompass(ItemStack stack) {
		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		return tracker != null && tracker.target().isPresent();
	}

	/**
	 * The dead-heads paragraph is folded into the last lesson's text rather
	 * than said as an extra line: held speech is capped at three per villager,
	 * and the graduation line takes the third slot.
	 */
	private static String wayBackClosing() {
		String base = "Which is the whole of the job in one sentence, if you want it that way. Not knowing where things are. "
			+ "Knowing how to get back to them.";
		if (!DEAD_HEADS) return base;

		return "And it matters more here than it would elsewhere, because of the way the ground keeps what you drop now. "
			+ "You don't leave a scattered pile rotting on a timer -- everything goes into the head standing where you fell, "
			+ "yours alone for a while and anybody's after that. So the bearing is the only part you're short of, and the "
			+ "bearing is exactly what this gives you. Walk it, don't run it. The head will wait. It's the lock that won't. "
			+ base;
	}

	private static List<LessonApi.Lesson> lessons() {
		return List.of(
			new LessonApi.Lesson(
				"You carry that map about in your hand, don't you. In your hand. Like a torch. -- There is a slot for it. Beside where your "
					+ "armour sits. Bring me a map you have actually walked in yourself, a proper drawn one, and I will show you where it goes.",
				"show {name} a filled map you drew yourself",
				"Not your hand. The slot beside your armour. That's what draws the corner.",
				"*takes it, turns it the right way up* Good. Now put it in the slot -- the one beside your chest piece with the little map "
					+ "printed on it. That is the entire difference. In your pack it is paper. In the slot it draws itself in the corner of "
					+ "your eye while you walk, and keeps drawing while you do something else.",
				"People carry them in the hotbar for years. A whole hotbar slot, gone, for a thing they look at twice an hour. -- Sorry. "
					+ "That one gets to me.",
				Items.FILLED_MAP, stack -> stack.is(Items.FILLED_MAP) && MapSlot.isMap(stack), 6),

			new LessonApi.Lesson(
				"Now bring me one you did not draw. A buried treasure map, a village map, one of the explorer ones -- I sell them, so do not "
					+ "look at me like that. Any of them will do. It matters that it is not the same kind of thing.",
				"show {name} a structure map -- treasure, explorer, or village, not one you drew",
				"That is not a filled map. It is its own item. Sixteen of them now.",
				"*taps it* Read the name on it. This is not a filled map, and has not been for a while now. They were split out: treasure, "
					+ "the mansion, ocean, the deep city, the mineshaft, five separate village ones, more I lose track of. Sixteen things "
					+ "where there used to be one.",
				"Which is why anything that goes looking for 'a filled map' walks straight past every map worth owning. The slot does not "
					+ "make that mistake -- it asks whether the paper has a drawing on it, not what the item is called. That is the right "
					+ "question, and it will still be the right question when somebody adds a seventeenth.",
				Items.BURIED_TREASURE_MAP, stack -> MapSlot.isMap(stack) && !stack.is(Items.FILLED_MAP), 6),

			new LessonApi.Lesson(
				"A compass next. Iron with a bit of redstone in the middle. Bring it, and leave the map behind -- I want you to see what it "
					+ "does with nothing to draw on.",
				"show {name} a plain compass",
				"No map needed. A needle and a number, turned the way you're facing.",
				"*holds it flat* With nothing in the map slot you still get something: a needle in the corner and a distance under it, swung "
					+ "round to match where you are looking. Half the time that is all you actually wanted. A bearing and a number.",
				"Understand what it is pointing at, though. World spawn. Not your bed, not your door -- the spot the world first put you, "
					+ "which by now is very likely somebody's wheat field. And only up here. Take it below and it has nothing to point at, "
					+ "so it points at nothing. That is not a broken compass.",
				Items.COMPASS, stack -> stack.is(Items.COMPASS) && !isBoundCompass(stack), 8),

			new LessonApi.Lesson(
				"Lodestone. Eight chiselled stone bricks round a single iron ingot -- cheaper than people expect, and they never look it up. "
					+ "Set it down somewhere that matters to you, click the compass on it, and bring that back.",
				"show {name} a compass bound to a lodestone",
				"Bound. It's forgotten spawn entirely, and that's permanent.",
				"*checks the needle* There. It does not point at spawn any more and it never will again -- once a compass is bound it is "
					+ "bound, and the stone is the only place it knows.",
				"And it goes quiet the moment you cross into another world. The stone is still standing there; you are simply not somewhere "
					+ "it can reach. Same for the plain one, same for the death one, three different reasons and the same blank needle. That "
					+ "is the part to hold on to: a needle with nothing to say is not a needle that has failed.",
				Items.COMPASS, stack -> stack.is(Items.COMPASS) && isBoundCompass(stack), 8),

			new LessonApi.Lesson(
				"Something you would never think to try. Put a compass on an enchanting table. -- No. I am serious. Sit with that a moment. "
					+ "Nobody puts a compass on a table, so nobody ever finds out. Bring me one that took, and you will see why I mention it.",
				"show {name} a compass carrying mob sight",
				"Blue is people. Red is what wants you dead. Green eats grass.",
				"*grins* There it is. Mob sight. It reads what is moving near you and puts them on the map as coloured dots -- blue for "
					+ "villagers, red for the hostile ones, green for the animals, orange for whatever fits none of those.",
				"A book off an anvil does it too, if you have been hoarding the odd ones nobody reads. And it works from the slot, which is "
					+ "the point -- you are not holding it, you are wearing it, and the corner tells you what is behind the trees.",
				Items.COMPASS, stack -> stack.is(Items.COMPASS) && hasMobSight(stack), 10),

			new LessonApi.Lesson(
				"Last one, and it is a long walk. A recovery compass. Eight echo shards round an ordinary compass, and the shards are only "
					+ "down in the deep cities, so I will not pretend this is an afternoon's errand. Bring one back and we are finished.",
				"show {name} a recovery compass",
				"It points at where you last died. Only in the world you died in.",
				"*handles it more carefully than the others* There. That points at the last place you died and at nothing else. Same rule as "
					+ "the rest about crossing over -- die below, come back up here, and it has nothing to say to you until you go back down.",
				wayBackClosing(),
				Items.RECOVERY_COMPASS, stack -> stack.is(Items.RECOVERY_COMPASS), 14));
	}
}
