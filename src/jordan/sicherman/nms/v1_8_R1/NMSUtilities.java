/**
 * 
 */
package jordan.sicherman.nms.v1_8_R1;

import java.util.HashMap;
import java.util.Map;

import jordan.sicherman.MyZ;
import jordan.sicherman.items.EngineerRecipe;
import jordan.sicherman.locales.Locale;
import jordan.sicherman.locales.LocaleMessage;
import jordan.sicherman.nms.utilities.NMS;
import jordan.sicherman.nms.v1_8_R1.anvil.CustomContainerAnvil;
import jordan.sicherman.nms.v1_8_R1.anvil.TileEntityCustomContainerAnvil;
import jordan.sicherman.nms.v1_8_R1.mobs.CustomEntityGiantZombie;
import jordan.sicherman.nms.v1_8_R1.mobs.CustomEntityGuard;
import jordan.sicherman.nms.v1_8_R1.mobs.CustomEntityPigZombie;
import jordan.sicherman.nms.v1_8_R1.mobs.CustomEntityZombie;
import jordan.sicherman.nms.v1_8_R1.mobs.SmartEntity;
import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.EntityHuman;
import net.minecraft.server.v1_8_R1.EntityInsentient;
import net.minecraft.server.v1_8_R1.EntityLiving;
import net.minecraft.server.v1_8_R1.EntityPlayer;
import net.minecraft.server.v1_8_R1.GroupDataEntity;
import net.minecraft.server.v1_8_R1.ITileEntityContainer;
import net.minecraft.server.v1_8_R1.World;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R1.inventory.CraftItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

/**
 * @author Jordan
 * 
 */
public class NMSUtilities {

	public static void attractEntity(LivingEntity entity, Location inLoc, long duration) {
		EntityLiving livingEntity = ((CraftLivingEntity) entity).getHandle();
		if (livingEntity instanceof SmartEntity) {
			((SmartEntity) livingEntity).setSmartTarget(inLoc, duration);
		}
	}

	public static boolean murderMessage(LocaleMessage message, Player murdered, Player murderer, Player[] audience) {
		String str = message.toString(false);
		if (str.indexOf("$0") < 0 || str.indexOf("$1") < 0) { return false; }

		String murdered_pre = murdered.getName();
		String murderer_pre = murderer.getName();

		Map<Locale, JSONMessage> cache = new HashMap<Locale, JSONMessage>();

		MyZ.log(message.filter(murdered_pre, murderer_pre).toString());

		for (Player p : audience) {
			Locale l = Locale.getLocale(p);
			JSONMessage m = cache.get(l);
			if (m == null) {
				str = message.toString(l, false);
				m = new JSONMessage(str.split("\\$0")[0]).then(ChatColor.getLastColors(str) + murdered_pre)
						.itemTooltip(murdered.getItemInHand()).then(ChatColor.getLastColors(str) + str.split("\\$0")[1].split("\\$1")[0])
						.then(ChatColor.getLastColors(str) + murderer_pre).itemTooltip(murderer.getItemInHand())
						.then(ChatColor.getLastColors(str) + (str.split("\\$1").length > 1 ? str.split("\\$1")[1] : ""));
				cache.put(l, m);
			}
			m.send(p);
		}
		return true;
	}

	public static boolean deathMessage(LocaleMessage message, Player died, ItemStack displayOnVariable, Player[] audience) {
		String s = message.toString(false);
		if (displayOnVariable != null && (s.indexOf("{") < 0 || s.indexOf("}") < 0)) { return false; }

		Map<Locale, JSONMessage> cache = new HashMap<Locale, JSONMessage>();

		String died_pre = died.getName();

		MyZ.log(message.smartFilter("\\{", "").smartFilter("\\}", "").filter(died_pre).toString());
		message.clearSmartFilter();

		for (Player p : audience) {
			Locale l = Locale.getLocale(p);
			JSONMessage msg = cache.get(l);
			if (msg == null) {
				String str = message.filter(died_pre).toString(l);
				msg = new JSONMessage(str.split("\\{")[0]).then(ChatColor.getLastColors(str) + str.split("\\{")[1].split("\\}")[0])
						.itemTooltip(displayOnVariable)
						.then(ChatColor.getLastColors(str) + (str.split("\\}").length > 1 ? str.split("\\}")[1] : ""));
				cache.put(l, msg);
			}
			msg.send(p);
		}
		return true;
	}

	public static void openAnvil(Player player, Block anvil, EngineerRecipe... recipe) {
		EntityHuman human = (EntityHuman) NMS.castToNMS(player);

		int x = player.getLocation().getBlockX(), y = player.getLocation().getBlockY(), z = player.getLocation().getBlockZ();

		if (anvil != null) {
			x = anvil.getX();
			y = anvil.getY();
			z = anvil.getZ();
		}

		BlockPosition position = new BlockPosition(x, y, z);

		if (!human.world.isStatic) {
			ITileEntityContainer itileentitycontainer = new TileEntityCustomContainerAnvil(human.world, position, anvil != null);
			human.openTileEntity(itileentitycontainer);

			if (recipe != null && recipe.length == 1) {
				((CustomContainerAnvil) human.activeContainer).activeRecipe = recipe[0];
				((CustomContainerAnvil) human.activeContainer).result.setItem(0, CraftItemStack.asNMSCopy(recipe[0].getOutput()));
				((CustomContainerAnvil) human.activeContainer).process.setItem(0, CraftItemStack.asNMSCopy(recipe[0].getInput(0)));
				((CustomContainerAnvil) human.activeContainer).process.setItem(1, CraftItemStack.asNMSCopy(recipe[0].getInput(1)));
			}
		}
	}

	public static boolean sendInventoryUpdate(Player player, int slot) {
		EntityPlayer human = (EntityPlayer) NMS.castToNMS(player);
		return ((CustomContainerAnvil) human.activeContainer).updateOn(slot);
	}

	public static void spawnEntity(Location inLoc, EntityType type) {
		EntityInsentient entity;
		World world = ((CraftWorld) inLoc.getWorld()).getHandle();

		switch (type) {
		case ZOMBIE:
			entity = new CustomEntityZombie(world);
			break;
		case GIANT:
			entity = new CustomEntityGiantZombie(world);
			break;
		case PIG_ZOMBIE:
			entity = new CustomEntityPigZombie(world);
			break;
		case SKELETON:
			entity = new CustomEntityGuard(world);
			break;
		default:
			return;
		}

		entity.setLocation(inLoc.getX(), inLoc.getY(), inLoc.getZ(), inLoc.getYaw(), inLoc.getPitch());
		entity.prepare(world.E(new BlockPosition(entity)), (GroupDataEntity) null);
		world.addEntity(entity, SpawnReason.CUSTOM);
	}
}
