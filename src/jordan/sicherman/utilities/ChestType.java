/**
 * 
 */
package jordan.sicherman.utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import jordan.sicherman.MyZ;
import jordan.sicherman.nms.utilities.CompatibilityManager;
import jordan.sicherman.particles.ParticleEffect;
import jordan.sicherman.utilities.configuration.ConfigEntries;
import jordan.sicherman.utilities.configuration.Configuration.CFiles;
import jordan.sicherman.utilities.configuration.FileUtilities;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.DirectionalContainer;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * @author Jordan
 * 
 */
public class ChestType {

	private static final List<ChestType> chestTypes = new ArrayList<ChestType>();
	private static final Random random = new Random();

	private final String key;
	private final Map<ItemProperties, ItemStack> contents = new HashMap<ItemProperties, ItemStack>();

	private ItemStack recent;

	public String getName() {
		return key;
	}

	public static ChestType[] values() {
		return chestTypes.toArray(new ChestType[0]);
	}

	private static class ItemProperties {
		final int min, max, prob;

		public ItemProperties(int min, int max, int prob) {
			this.min = min;
			this.max = max;
			this.prob = prob;
		}
	}

	public ItemStack[] generate() {
		List<ItemStack> items = new ArrayList<ItemStack>();

		for (ItemProperties properties : contents.keySet()) {
			if (properties.prob > 0 && random.nextInt(101) <= properties.prob) {
				int amount = random.nextInt(properties.max - properties.min + 1) + properties.min;
				if (amount > 0) {
					ItemStack item = contents.get(properties).clone();
					item.setAmount(amount);
					items.add(item);
				}
			}
		}

		return items.toArray(new ItemStack[0]);
	}

	public static ChestType getType(Block block) {
		ConfigurationSection section = ConfigEntries.CHEST_LOCATIONS.<ConfigurationSection> getValue();
		for (String key : section.getKeys(false)) {
			MyZ.debug("Measuring to a SerializableLocation(" + key + ")");
			if (block.getLocation().distanceSquared(SerializableLocation.deserialize(key)) == 0) { return ChestType.fromString(section
					.getString(key + ".type")); }
		}
		return null;
	}

	private static ChestType fromString(String key) {
		for (ChestType type : values()) {
			if (type.key.equals(key)) { return type; }
		}
		return null;
	}

	public static void despawn(Block block) {
		ConfigurationSection section = ConfigEntries.CHEST_LOCATIONS.<ConfigurationSection> getValue();
		String key = SerializableLocation.fromLocation(block.getLocation()).serialize().replaceAll("\\.0", "");
		if (section.contains(key)) {
			section.set(key + ".respawn_time", System.currentTimeMillis() + ConfigEntries.CHEST_RESPAWN_TIME.<Integer> getValue() * 1000);
			if (block.getType() != Material.AIR) {
				block.setType(Material.AIR);
				ParticleEffect.CLOUD.display(-0.5f, 0f, -0.5f, 0.025f, 25, block.getLocation(), 20.0);
			}
		}
	}

	public static void respawn(final Block block, boolean force) {
		final ConfigurationSection section = ConfigEntries.CHEST_LOCATIONS.<ConfigurationSection> getValue();
		final String key = SerializableLocation.fromLocation(block.getLocation()).serialize().replaceAll("\\.0", "");
		if (section.contains(key)) {
			long respawn = section.getLong(key + ".respawn_time");
			if (respawn < 0) { return; }
			if (force || System.currentTimeMillis() >= respawn) {
				section.set(key + ".respawn_time", -1);

				new BukkitRunnable() {
					@Override
					public void run() {
						Material matl = Material.valueOf(section.getString(key + ".material"));
						if (block.getType() == matl) {
							if (((Chest) block.getState()).getBlockInventory().getContents().length != 0) {
								MyZ.debug("Attempted to respawn a not-empty chest.");
								return;
							}
						} else {
							MyZ.debug("Populating a chest");
							block.setType(matl);
						}

						Chest chest = (Chest) block.getState();
						((DirectionalContainer) block.getState().getData()).setFacingDirection(BlockFace.valueOf(section.getString(key
								+ ".orientation")));
						CompatibilityManager.renameChest(chest, section.getString(key + ".type"));
						block.getState().update();

						ChestType type = ChestType.fromName(section.getString(key + ".type"));
						if (type != null) {
							chest.getBlockInventory().addItem(type.generate());
						}
					}
				}.runTaskLater(MyZ.instance, 0L);
			}
		}
	}

	public static void setType(Block block, ChestType type) {
		ConfigurationSection section = ConfigEntries.CHEST_LOCATIONS.<ConfigurationSection> getValue();
		String key = SerializableLocation.fromLocation(block.getLocation()).serialize().replaceAll("\\.0", "");

		if (type == null) {
			section.set(key, null);
		} else {
			section.set(key + ".material", block.getType().toString());
			section.set(key + ".orientation", ((DirectionalContainer) block.getState().getData()).getFacing().name());
			section.set(key + ".type", type.key);
			section.set(key + ".respawn_time", -1);
		}
		FileUtilities.save(CFiles.CHESTS);

		respawn(block, true);
	}

	public static ChestType fromName(String name) {
		for (ChestType type : values()) {
			if (type.key.equals(name)) { return type; }
		}
		return null;
	}

	public ChestType(String key) {
		this.key = key;
		chestTypes.add(this);

		ConfigurationSection section = ConfigEntries.CHEST_TYPES.<ConfigurationSection> getValue().getConfigurationSection(key);

		if (section != null) {
			for (String cKey : section.getKeys(false)) {
				ItemStack item = section.getConfigurationSection(cKey).getItemStack("item").clone();

				contents.put(new ItemProperties(section.getConfigurationSection(cKey).getInt("amount_minimum"), section
						.getConfigurationSection(cKey).getInt("amount_maximum"), section.getConfigurationSection(cKey)
						.getInt("probability")), item);
			}
		}
	}

	public static void load() {
		chestTypes.clear();

		for (String key : ConfigEntries.CHEST_TYPES.<ConfigurationSection> getValue().getKeys(false)) {
			new ChestType(key);
		}
	}

	public static ChestType nextType(ChestType type) {
		MyZ.debug("Currently " + (type != null ? type.getName() : ""));

		if (values().length == 0) { return null; }

		if (type == null) {
			MyZ.debug("Becoming " + values()[0].getName());
			return chestTypes.get(0);
		}

		for (int i = 0; i <= values().length - 2; i++) {
			if (type.equals(values()[i])) {
				MyZ.debug("Becoming " + values()[i + 1].getName());
				return values()[i + 1];
			}
		}

		MyZ.debug("Becoming empty");
		return null;
	}

	public static void create(String name) {
		new ChestType(name);
	}

	public void addItem(ItemStack item) {
		recent = item.clone();
	}

	public void setRecentProperties(int min, int max, int prob) {
		if (recent != null) {
			contents.put(new ItemProperties(min, max, prob), recent);

			String uuid = UUID.randomUUID().toString();
			ConfigurationSection section = ConfigEntries.CHEST_TYPES.<ConfigurationSection> getValue().getConfigurationSection(key);

			if (section == null) {
				section = ConfigEntries.CHEST_TYPES.<ConfigurationSection> getValue().createSection(key);
			}
			section.set(uuid + ".probablity", prob);
			section.set(uuid + ".amount_minimum", min);
			section.set(uuid + ".amount_maximum", max);
			section.set(uuid + ".item", recent);

			FileUtilities.save(CFiles.CHESTS);

			recent = null;
		}
	}

	public void remove() {
		ConfigurationSection chests = ConfigEntries.CHEST_LOCATIONS.<ConfigurationSection> getValue();
		for (String key : new HashSet<String>(chests.getKeys(false))) {
			if (key.equals(chests.get(key + ".type"))) {
				respawn(SerializableLocation.deserialize(chests.getString(key)).getBlock(), true);
				chests.set(key, null);
			}
		}

		ConfigEntries.CHEST_TYPES.<ConfigurationSection> getValue().set(key, null);
		FileUtilities.save(CFiles.CHESTS);
	}
}
