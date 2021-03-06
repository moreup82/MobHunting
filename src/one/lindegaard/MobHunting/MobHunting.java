package one.lindegaard.MobHunting;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.milkbowl.vault.economy.Economy;
import one.lindegaard.MobHunting.achievements.*;
import one.lindegaard.MobHunting.bounty.BountyManager;
import one.lindegaard.MobHunting.commands.BountyCommand;
import one.lindegaard.MobHunting.commands.CheckGrindingCommand;
import one.lindegaard.MobHunting.commands.ClearGrindingCommand;
import one.lindegaard.MobHunting.commands.CommandDispatcher;
import one.lindegaard.MobHunting.commands.DatabaseCommand;
import one.lindegaard.MobHunting.commands.LeaderboardCommand;
import one.lindegaard.MobHunting.commands.LearnCommand;
import one.lindegaard.MobHunting.commands.ListAchievementsCommand;
import one.lindegaard.MobHunting.commands.MuteCommand;
import one.lindegaard.MobHunting.commands.NpcCommand;
import one.lindegaard.MobHunting.commands.RegionCommand;
import one.lindegaard.MobHunting.commands.ReloadCommand;
import one.lindegaard.MobHunting.commands.SelectCommand;
import one.lindegaard.MobHunting.commands.TopCommand;
import one.lindegaard.MobHunting.commands.UpdateCommand;
import one.lindegaard.MobHunting.commands.VersionCommand;
import one.lindegaard.MobHunting.commands.WhitelistAreaCommand;
import one.lindegaard.MobHunting.compatability.BattleArenaCompat;
import one.lindegaard.MobHunting.compatability.BattleArenaHelper;
import one.lindegaard.MobHunting.compatability.CitizensCompat;
import one.lindegaard.MobHunting.compatability.CompatibilityManager;
import one.lindegaard.MobHunting.compatability.DisguiseCraftCompat;
import one.lindegaard.MobHunting.compatability.DisguisesHelper;
import one.lindegaard.MobHunting.compatability.EssentialsCompat;
import one.lindegaard.MobHunting.compatability.IDisguiseCompat;
import one.lindegaard.MobHunting.compatability.LibsDisguisesCompat;
import one.lindegaard.MobHunting.compatability.MinigamesCompat;
import one.lindegaard.MobHunting.compatability.MobArenaCompat;
import one.lindegaard.MobHunting.compatability.MobArenaHelper;
import one.lindegaard.MobHunting.compatability.MyPetCompat;
import one.lindegaard.MobHunting.compatability.MythicMobsCompat;
import one.lindegaard.MobHunting.compatability.PVPArenaCompat;
import one.lindegaard.MobHunting.compatability.PVPArenaHelper;
import one.lindegaard.MobHunting.compatability.WorldEditCompat;
import one.lindegaard.MobHunting.compatability.WorldGuardCompat;
import one.lindegaard.MobHunting.events.MobHuntKillEvent;
import one.lindegaard.MobHunting.leaderboard.LeaderboardManager;
import one.lindegaard.MobHunting.modifier.*;
import one.lindegaard.MobHunting.storage.DataStoreException;
import one.lindegaard.MobHunting.storage.DataStoreManager;
import one.lindegaard.MobHunting.storage.IDataStore;
import one.lindegaard.MobHunting.storage.MySQLDataStore;
import one.lindegaard.MobHunting.storage.PlayerSettings;
import one.lindegaard.MobHunting.storage.SQLiteDataStore;
import one.lindegaard.MobHunting.update.UpdateHelper;
import one.lindegaard.MobHunting.util.Misc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;

public class MobHunting extends JavaPlugin implements Listener {

	// Constants
	private final static String pluginName = "mobhunting";
	// private String pluginVersion = "";

	private static Economy mEconomy;
	private static MobHunting instance;

	private MobHuntingManager mMobHuntingManager;
	private AreaManager mAreaManager;
	private LeaderboardManager mLeaderboardManager;
	private AchievementManager mAchievementManager;
	private ParticleManager mParticleManager = new ParticleManager();
	private MetricsManager mMetricsManager;

	private IDataStore mStore;
	private DataStoreManager mStoreManager;
	private static ConfigManager mConfig;
	private HashMap<UUID, PlayerSettings> mPlayerSettings = new HashMap<UUID, PlayerSettings>();

	private WeakHashMap<LivingEntity, DamageInformation> mDamageHistory = new WeakHashMap<LivingEntity, DamageInformation>();

	private Set<IModifier> mModifiers = new HashSet<IModifier>();

	private Random mRand = new Random();

	private boolean mInitialized = false;

	@Override
	public void onLoad() {
	}

	@Override
	public void onEnable() {
		instance = this;

		mMobHuntingManager = new MobHuntingManager(this);

		UpdateHelper.setCurrentJarFile(instance.getFile().getName());

		Messages.exportDefaultLanguages();

		mConfig = new ConfigManager(new File(getDataFolder(), "config.yml"));

		if (mConfig.loadConfig())
			mConfig.saveConfig();
		else
			throw new RuntimeException(Messages.getString(pluginName + ".config.fail"));

		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager()
				.getRegistration(Economy.class);
		if (economyProvider == null) {
			instance = null;
			getLogger().severe(Messages.getString(pluginName + ".hook.econ"));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		mEconomy = economyProvider.getProvider();

		mAreaManager = new AreaManager(this);

		if (mConfig.databaseType.equalsIgnoreCase("mysql"))
			mStore = new MySQLDataStore();
		else
			mStore = new SQLiteDataStore();

		try {
			mStore.initialize();
		} catch (DataStoreException e) {
			e.printStackTrace();

			try {
				mStore.shutdown();
			} catch (DataStoreException e1) {
				e1.printStackTrace();
			}
			setEnabled(false);
			return;
		}

		mStoreManager = new DataStoreManager(mStore);

		// Handle compatability stuff
		registerPlugin(EssentialsCompat.class, "Essentials");
		registerPlugin(WorldEditCompat.class, "WorldEdit");
		registerPlugin(WorldGuardCompat.class, "WorldGuard");
		registerPlugin(MythicMobsCompat.class, "MythicMobs");
		registerPlugin(CitizensCompat.class, "Citizens");
		registerPlugin(MinigamesCompat.class, "Minigames");
		registerPlugin(MyPetCompat.class, "MyPet");
		registerPlugin(MobArenaCompat.class, "MobArena");
		registerPlugin(PVPArenaCompat.class, "PVPArena");
		registerPlugin(LibsDisguisesCompat.class, "LibsDisguises");
		registerPlugin(DisguiseCraftCompat.class, "DisguiseCraft");
		registerPlugin(IDisguiseCompat.class, "iDisguise");
		registerPlugin(BattleArenaCompat.class, "BattleArena");

		// register commands
		CommandDispatcher cmd = new CommandDispatcher("mobhunt",
				Messages.getString("mobhunting.command.base.description") + getDescription().getVersion());
		getCommand("mobhunt").setExecutor(cmd);
		getCommand("mobhunt").setTabCompleter(cmd);
		cmd.registerCommand(new ListAchievementsCommand());
		cmd.registerCommand(new CheckGrindingCommand());
		cmd.registerCommand(new TopCommand());
		cmd.registerCommand(new LeaderboardCommand());
		cmd.registerCommand(new ClearGrindingCommand());
		cmd.registerCommand(new WhitelistAreaCommand());
		if (CompatibilityManager.isPluginLoaded(WorldEditCompat.class))
			cmd.registerCommand(new SelectCommand());
		if (CompatibilityManager.isPluginLoaded(WorldGuardCompat.class))
			cmd.registerCommand(new RegionCommand());
		cmd.registerCommand(new ReloadCommand());
		cmd.registerCommand(new UpdateCommand());
		cmd.registerCommand(new VersionCommand());
		cmd.registerCommand(new LearnCommand(this));
		cmd.registerCommand(new MuteCommand(this));
		if (CompatibilityManager.isPluginLoaded(CitizensCompat.class)) {
			cmd.registerCommand(new NpcCommand());
		}
		cmd.registerCommand(new DatabaseCommand());

		// TODO: enable
		if (!mConfig.disablePlayerBounties) {
			debug("Enabling Bounty command");
			cmd.registerCommand(new BountyCommand(this));
			BountyManager.initialize(this);
		}

		registerModifiers();

		getServer().getPluginManager().registerEvents(this, this);

		mAchievementManager = new AchievementManager();

		// this is only need when server owner upgrades from very old version of
		// Mobhunting
		if (mAchievementManager.upgradeAchievements())
			mStoreManager.waitForUpdates();

		for (Player player : mMobHuntingManager.getOnlinePlayers())
			mAchievementManager.load(player);

		mLeaderboardManager = new LeaderboardManager(this);

		mMetricsManager = new MetricsManager();
		mMetricsManager.startMetrics();

		UpdateHelper.hourlyUpdateCheck(getServer().getConsoleSender(), mConfig.updateCheck, false);

		mInitialized = true;

		if (mMobHuntingManager.getOnlinePlayersAmount() > 0)
			debug("onReload: loading %s players from the database", mMobHuntingManager.getOnlinePlayersAmount());
		for (Player player : mMobHuntingManager.getOnlinePlayers()) {
			boolean learning_mode = getDataStore().getPlayerSettings(player).isLearningMode();
			boolean muted = getDataStore().getPlayerSettings(player).isMuted();
			addPlayerSettings(player, new PlayerSettings(player, learning_mode, muted));
		}

	}

	private void registerPlugin(@SuppressWarnings("rawtypes") Class c, String pluginName) {
		try {
			CompatibilityManager.register(c, pluginName);
		} catch (Exception e) {
			getServer().getConsoleSender()
					.sendMessage(ChatColor.RED + "[MobHunting][ERROR] MobHunting could not register with [" + pluginName
							+ "] please check if [" + pluginName + "] is compatible with the server ["
							+ getServer().getBukkitVersion() + "]");
			if (getConfigManager().killDebug)
				e.printStackTrace();
		}
	}

	@Override
	public void onDisable() {
		if (!mInitialized)
			return;

		mLeaderboardManager.shutdown();
		mAreaManager.shutdown();
		// TODO: enable
		BountyManager.shutdown();

		mModifiers.clear();

		try {
			mStoreManager.shutdown();
			mStore.shutdown();
		} catch (DataStoreException e) {
			e.printStackTrace();
		}
		CitizensCompat.shutdown();
	}

	private void registerModifiers() {
		mModifiers.add(new BrawlerBonus());
		mModifiers.add(new ProSniperBonus());
		mModifiers.add(new SniperBonus());
		mModifiers.add(new ReturnToSenderBonus());
		mModifiers.add(new ShoveBonus());
		mModifiers.add(new SneakyBonus());
		mModifiers.add(new FriendleFireBonus());
		mModifiers.add(new BonusMobBonus());
		mModifiers.add(new CriticalModifier());

		mModifiers.add(new FlyingPenalty());
		mModifiers.add(new GrindingPenalty());
		mModifiers.add(new Undercover());
		mModifiers.add(new CoverBlown());
		mModifiers.add(new RankBonus());
		mModifiers.add(new DifficultyBonus());
		// Check if horses exist
		try {
			Class.forName("org.bukkit.entity.Horse");
			mModifiers.add(new MountedBonus());
		} catch (ClassNotFoundException e) {
		}
	}

	public static Economy getEconomy() {
		return mEconomy;
	}

	public static ConfigManager getConfigManager() {
		return mConfig;
	}

	public void registerModifier(IModifier modifier) {
		mModifiers.add(modifier);
	}

	public static void debug(String text, Object... args) {
		if (mConfig.killDebug)
			instance.getLogger().info("[Debug] " + String.format(text, args));
	}

	public static void learn(Player player, String text, Object... args) {
		if (player instanceof Player && !CitizensCompat.isNPC(player)) {
			if (instance.getPlayerSettings(player).isLearningMode())
				player.sendMessage(ChatColor.AQUA + Messages.getString("mobhunting.learn.prefix") + " "
						+ String.format(text, args));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerDeath(PlayerDeathEvent event) {
		if (!MobHuntingManager.isHuntEnabledInWorld(event.getEntity().getWorld())
				|| !mMobHuntingManager.isHuntEnabled(event.getEntity()))
			return;

		HuntData data = mMobHuntingManager.getHuntData(event.getEntity());
		if (data.getKillstreakLevel() != 0)
			event.getEntity().sendMessage(
					ChatColor.RED + "" + ChatColor.ITALIC + Messages.getString("mobhunting.killstreak.ended"));
		data.setKillStreak(0);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player))
			return;

		if (!MobHuntingManager.isHuntEnabledInWorld(event.getEntity().getWorld())
				|| !mMobHuntingManager.isHuntEnabled((Player) event.getEntity()))
			return;

		Player player = (Player) event.getEntity();
		HuntData data = mMobHuntingManager.getHuntData(player);
		if (data.getKillstreakLevel() != 0)
			player.sendMessage(
					ChatColor.RED + "" + ChatColor.ITALIC + Messages.getString("mobhunting.killstreak.ended"));
		data.setKillStreak(0);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onSkeletonShoot(ProjectileLaunchEvent event) {
		// TODO: can Skeleton use other weapons than an Arrow?
		if (!(event.getEntity() instanceof Arrow) || !(event.getEntity().getShooter() instanceof Skeleton)
				|| !MobHuntingManager.isHuntEnabledInWorld(event.getEntity().getWorld()))
			return;

		Skeleton shooter = (Skeleton) event.getEntity().getShooter();

		if (shooter.getTarget() instanceof Player && mMobHuntingManager.isHuntEnabled((Player) shooter.getTarget())
				&& ((Player) shooter.getTarget()).getGameMode() != GameMode.CREATIVE) {
			DamageInformation info = null;
			info = mDamageHistory.get(shooter);

			if (info == null)
				info = new DamageInformation();

			info.time = System.currentTimeMillis();

			info.attacker = (Player) shooter.getTarget();

			info.attackerPosition = shooter.getTarget().getLocation().clone();
			mDamageHistory.put(shooter, info);
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onMobDamage(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof LivingEntity)
				|| !MobHuntingManager.isHuntEnabledInWorld(event.getEntity().getWorld()))
			return;
		Entity damager = event.getDamager();
		Entity damaged = event.getEntity();
		// check if damager or damaged is Sentry. Only Sentry gives a reward.
		if (CitizensCompat.isNPC(damager))
			if (!CitizensCompat.isSentry(damager))
				return;
		if (CitizensCompat.isNPC(damaged))
			if (!CitizensCompat.isSentry(damaged))
				return;
		if (CompatibilityManager.isPluginLoaded(WorldGuardCompat.class)) {
			if ((damager instanceof Player) || MyPetCompat.isMyPet(damager)) {
				RegionManager regionManager = WorldGuardCompat.getWorldGuardPlugin()
						.getRegionManager(damager.getWorld());
				ApplicableRegionSet set = regionManager.getApplicableRegions(damager.getLocation());
				if (set != null) {
					if (!set.allows(DefaultFlag.MOB_DAMAGE)) {
						debug("KillBlocked: %s is hiding in WG region with MOB_DAMAGE %s", damager.getName(),
								set.allows(DefaultFlag.MOB_DAMAGE));
						return;
					}
				}
			}
		}

		DamageInformation info = null;
		info = mDamageHistory.get(damaged);
		if (info == null)
			info = new DamageInformation();

		info.time = System.currentTimeMillis();

		Player cause = null;
		ItemStack weapon = null;

		if (damager instanceof Player) {
			cause = (Player) damager;
			// TODO:
			// if (cause.is
		}

		boolean projectile = false;
		if (damager instanceof Projectile) {
			if (((Projectile) damager).getShooter() instanceof Player)
				cause = (Player) ((Projectile) damager).getShooter();

			if (damager instanceof ThrownPotion)
				weapon = ((ThrownPotion) damager).getItem();

			info.mele = false;
			projectile = true;
		} else
			info.mele = true;

		if (damager instanceof Wolf && ((Wolf) damager).isTamed() && ((Wolf) damager).getOwner() instanceof Player) {
			cause = (Player) ((Wolf) damager).getOwner();

			info.mele = false;
			info.wolfAssist = true;
		}

		if (weapon == null && cause != null)
			weapon = cause.getItemInHand();

		if (weapon != null)
			info.weapon = weapon;

		// Take note that a weapon has been used at all
		if (info.weapon != null
				&& (Misc.isSword(info.weapon) || Misc.isAxe(info.weapon) || Misc.isPick(info.weapon) || projectile))
			info.usedWeapon = true;

		if (cause != null) {
			if (cause != info.attacker) {
				info.assister = info.attacker;
				info.lastAssistTime = info.lastAttackTime;
			}

			info.lastAttackTime = System.currentTimeMillis();

			info.attacker = cause;
			if (cause.isFlying() && !cause.isInsideVehicle())
				info.wasFlying = true;

			info.attackerPosition = cause.getLocation().clone();

			if (!info.playerUndercover)
				if (DisguisesHelper.isDisguised(cause)) {
					if (DisguisesHelper.isDisguisedAsAgresiveMob(cause)) {
						debug("[MobHunting] %s was under cover - diguised as an agressive mob", cause.getName());
						info.playerUndercover = true;
					} else
						debug("[MobHunting] %s was under cover - diguised as an passive mob", cause.getName());
					if (mConfig.removeDisguiseWhenAttacking) {
						DisguisesHelper.undisguiseEntity(cause);
						if (cause instanceof Player)
							cause.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
									+ Messages.getString("bonus.undercover.message", "cause", cause.getName()));
						if (damaged instanceof Player)
							damaged.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
									+ Messages.getString("bonus.undercover.message", "cause", cause.getName()));
					}
				}

			if (!info.mobCoverBlown)
				if (DisguisesHelper.isDisguised(damaged)) {
					if (DisguisesHelper.isDisguisedAsAgresiveMob(damaged)) {
						debug("[MobHunting] %s Cover blown, diguised as an agressive mob", damaged.getName());
						info.mobCoverBlown = true;
					} else
						debug("[MobHunting] %s Cover Blown, diguised as an passive mob", damaged.getName());
					if (mConfig.removeDisguiseWhenAttacked) {
						DisguisesHelper.undisguiseEntity(damaged);
						if (damaged instanceof Player)
							damaged.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
									+ Messages.getString("bonus.coverblown.message", "damaged", damaged.getName()));
						if (cause instanceof Player)
							cause.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
									+ Messages.getString("bonus.coverblown.message", "damaged", damaged.getName()));
					}
				}

			mDamageHistory.put((LivingEntity) damaged, info);
		}
	}

	@SuppressWarnings({ "unused", "deprecation" })
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onMobDeath(EntityDeathEvent event) {
		LivingEntity killed = event.getEntity();
		Player killer = killed.getKiller();
		if (killer == null)
			return;

		if (!MobHuntingManager.isHuntEnabledInWorld(killed.getWorld())) {
			if (CompatibilityManager.isPluginLoaded(WorldGuardCompat.class) && WorldGuardCompat.isEnabledInConfig()) {
				if (killer instanceof Player || MyPetCompat.isMyPet(killer)) {
					ApplicableRegionSet set = WorldGuardCompat.getWorldGuardPlugin().getRegionManager(killer.getWorld())
							.getApplicableRegions(killer.getLocation());
					if (set.size() > 0) {
						if (set.allows(WorldGuardCompat.getMobHuntingFlag())) {
							debug("KillBlocked %s(%d): Mobhunting disabled in world '%s'"
									+ ",but MobHunting flag is (%s)", killed.getType(), killed.getEntityId(),
									killed.getWorld().getName(), set.allows(WorldGuardCompat.getMobHuntingFlag()));
						} else {
							debug("KillBlocked %s(%d): Mobhunting disabled in world '%s',"
									+ " and MobHunting flag is '%s')", killed.getType(), killed.getEntityId(),
									killed.getWorld().getName(), set.allows(WorldGuardCompat.getMobHuntingFlag()));
							learn(killer, Messages.getString("mobhunting.learn.disabled1"));
							return;
						}
					} else {
						debug("KillBlocked %s(%d): Mobhunting disabled in world %s, "
								+ "WG is supported, but player not in a WG region.", killed.getType(),
								killed.getEntityId(), killed.getWorld().getName());
						learn(killer, Messages.getString("mobhunting.learn.disabled2"));
						return;
					}
				}
				// killer is not a player - MobHunting is allowed
			} else {
				// MobHunting is NOT allowed in world and no support for WG
				// reject.
				debug("KillBlocked: MobHunting disabled in world and Worldguard is not supported");
				learn(killer, Messages.getString("mobhunting.learn.disabled2"));
				return;
			}

			// MobHunting is allowed in this world,
			// Continue to ned if... (Do NOTHING).
		}

		if (CompatibilityManager.isPluginLoaded(WorldGuardCompat.class) && WorldGuardCompat.isEnabledInConfig()) {
			if (killer instanceof Player || MyPetCompat.isMyPet(killer)) {

				ApplicableRegionSet set = WorldGuardCompat.getWorldGuardPlugin().getRegionManager(killer.getWorld())
						.getApplicableRegions(killer.getLocation());

				if (set.size() > 0) {
					debug("Found %s Worldguard region(s): MOB_DAMAGE flag is %s", set.size(),
							set.allows(DefaultFlag.MOB_DAMAGE));
					if (!set.allows(DefaultFlag.MOB_DAMAGE)) {
						debug("KillBlocked: %s is hiding in WG region with MOB_DAMAGE %s", killer.getName(),
								set.allows(DefaultFlag.MOB_DAMAGE));
						learn(killer, Messages.getString("mobhunting.learn.mob-damage-flag"));
						return;
					} else if (!set.allows(WorldGuardCompat.getMobHuntingFlag())) {
						debug("KillBlocked: %s is hiding in WG region with MOBHUNTING FLAG %s", killer.getName(),
								set.allows(WorldGuardCompat.getMobHuntingFlag()));
						learn(killer, Messages.getString("mobhunting.learn.mobhunting-deny"));
						return;
					}

				}
			}
		}

		if (killed instanceof Player) {
			if (MobArenaCompat.isEnabledInConfig() && MobArenaHelper.isPlayingMobArena((Player) killed)) {
				debug("KillBlocked: %s was killed while playing MobArena.", killed.getName());
				return;
			} else if (PVPArenaCompat.isEnabledInConfig() && PVPArenaHelper.isPlayingPVPArena((Player) killed)) {
				debug("KillBlocked: %s was killed while playing PvpArena.", killed.getName());
				return;
			} else if (BattleArenaCompat.isEnabledInConfig()
					&& BattleArenaHelper.isPlayingBattleArena((Player) killed)) {
				debug("KillBlocked: %s was killed while playing BattleArena.", killed.getName());
				return;
			} else if (killer instanceof Player && !mConfig.pvpAllowed) {
				debug("KillBlocked: PVP not allowed. %s killed %s.", killer.getName(), killed.getName());
				return;
			}
		}

		if (MythicMobsCompat.isSupported()) {
			if (killed.hasMetadata("MH:MythicMob"))
				if (killer instanceof Player)
					debug("%s killed a MythicMob", killer.getName());
		}

		if (CitizensCompat.isEnabledInConfig() && CitizensCompat.isCitizensSupported()
				&& CitizensCompat.isNPC(killed)) {
			if (CitizensCompat.isSentry(killed))
				if (killer instanceof Player)
					debug("%s killed Sentry npc-%s (name=%s)", killer.getName(), CitizensCompat.getNPCId(killed),
							CitizensCompat.getNPCName(killed));
		}

		if (killer instanceof Player) {
			if (MobArenaCompat.isEnabledInConfig() && MobArenaHelper.isPlayingMobArena(killer)
					&& !mConfig.mobarenaGetRewards) {
				debug("KillBlocked: %s is currently playing MobArena.", killer.getName());
				learn(killer, Messages.getString("mobhunting.learn.mobarena"));
				return;
			} else if (PVPArenaCompat.isEnabledInConfig() && PVPArenaHelper.isPlayingPVPArena(killer)
					&& !mConfig.pvparenaGetRewards) {
				debug("KillBlocked: %s is currently playing PvpArena.", killer.getName());
				learn(killer, Messages.getString("mobhunting.learn.pvparena"));
				return;
			} else if (BattleArenaCompat.isEnabledInConfig() && BattleArenaHelper.isPlayingBattleArena(killer)) {
				debug("KillBlocked: %s is currently playing BattleArena.", killer.getName());
				learn(killer, Messages.getString("mobhunting.learn.battlearena"));
				return;
			} else if (EssentialsCompat.isSupported()) {
				if (EssentialsCompat.isGodModeEnabled(killer)) {
					debug("KillBlocked: %s is in God mode", killer.getName());
					learn(killer, Messages.getString("mobhunting.learn.godmode"));
					return;
				} else if (EssentialsCompat.isVanishedModeEnabled(killer)) {
					debug("KillBlocked: %s is in Vanished mode", killer.getName());
					learn(killer, Messages.getString("mobhunting.learn.vanished"));
					return;
				}

			}

			if (!hasPermissionToKillMob(killer, killed)) {
				debug("KillBlocked: %s has not permission to kill %s.", killer.getName(), killed.getName());
				learn(killer, Messages.getString("mobhunting.learn.no-permission", "killed-mob", killed.getName()));
				return;
			}
		}

		if (mConfig.getBaseKillPrize(event.getEntity()) == 0 && mConfig.getKillConsoleCmd(killed).equals("")) {
			debug("KillBlocked %s(%d): There is no reward for this Mob/Player", killed.getType(), killed.getEntityId());
			if (killed != null)
				learn(killer, Messages.getString("mobhunting.learn.no-reward", "killed", killed.getName()));
			return;
		}

		if (killed.hasMetadata("MH:blocked")) {
			debug("KillBlocked %s(%d): Mob has MH:blocked meta (probably spawned from a mob spawner)", killed.getType(),
					killed.getEntityId());
			if (killed != null) {
				learn(killer, Messages.getString("mobhunting.learn.mobspawner", "killed", killed.getName()));
			}
			return;
		}

		if (!mMobHuntingManager.isHuntEnabled(killer)) {
			debug("KillBlocked %s: Hunting is disabled for player", killer.getName());
			learn(killer, Messages.getString("mobhunting.learn.huntdisabled"));
			return;
		}

		if (killer.getGameMode() == GameMode.CREATIVE) {
			debug("KillBlocked %s: In creative mode", killer.getName());
			learn(killer, Messages.getString("mobhunting.learn.creative"));
			return;
		}

		DamageInformation info = null;
		if (killed instanceof LivingEntity && mDamageHistory.containsKey((LivingEntity) killed)) {
			info = mDamageHistory.get(killed);

			if (System.currentTimeMillis() - info.time > mConfig.assistTimeout * 1000)
				info = null;
			else if (killer == null)
				killer = info.attacker;
		}

		EntityDamageByEntityEvent lastDamageCause = null;

		if (killed.getLastDamageCause() instanceof EntityDamageByEntityEvent)
			lastDamageCause = (EntityDamageByEntityEvent) killed.getLastDamageCause();

		if (info == null) {
			info = new DamageInformation();
			info.time = System.currentTimeMillis();
			info.lastAttackTime = info.time;
			info.attacker = killer;
			info.attackerPosition = killer.getLocation();
			info.usedWeapon = true;
		}

		if ((System.currentTimeMillis() - info.lastAttackTime) > mConfig.killTimeout * 1000) {
			debug("KillBlocked %s: Last damage was too long ago", killer.getName());
			return;
		}

		if (info.weapon == null)
			info.weapon = new ItemStack(Material.AIR);

		boolean killer_muted = false;
		boolean killed_muted = false;
		if (mPlayerSettings.containsKey(killer.getUniqueId())) {
			killer_muted = mPlayerSettings.get(killer.getUniqueId()).isMuted();
			debug("killer.getMuteMode=%s", killer_muted);
		}
		if (mPlayerSettings.containsKey(killed.getUniqueId()))
			killed_muted = mPlayerSettings.get(killed.getUniqueId()).isMuted();

		if (!info.playerUndercover)
			if (DisguisesHelper.isDisguised(killer)) {
				if (DisguisesHelper.isDisguisedAsAgresiveMob(killer)) {
					info.playerUndercover = true;
				} else if (mConfig.removeDisguiseWhenAttacking) {
					DisguisesHelper.undisguiseEntity(killer);
					if (killer instanceof Player && !killer_muted)
						killer.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
								+ Messages.getString("bonus.undercover.message", "cause", killer.getName()));
					if (killed instanceof Player && !killed_muted)
						killed.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
								+ Messages.getString("bonus.undercover.message", "cause", killer.getName()));
				}

			}

		if (!info.mobCoverBlown)
			if (DisguisesHelper.isDisguised(killed)) {
				if (DisguisesHelper.isDisguisedAsAgresiveMob(killed)) {
					info.mobCoverBlown = true;
				}
				if (mConfig.removeDisguiseWhenAttacked) {
					DisguisesHelper.undisguiseEntity(killed);
					if (killed instanceof Player && !killed_muted)
						killed.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
								+ Messages.getString("bonus.coverblown.message", "damaged", killed.getName()));
					if (killer instanceof Player && !killer_muted)
						killer.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
								+ Messages.getString("bonus.coverblown.message", "damaged", killed.getName()));
				}
			}

		HuntData data = mMobHuntingManager.getHuntData(killer);
		// if (data == null)
		// debug("DATA WAS NULL!!!!!!!!!!!!!!!!");

		Misc.handleKillstreak(killer);

		// Record kills that are still within a small area
		Location loc = killed.getLocation();

		Area detectedGrindingArea = AreaManager.getGrindingArea(loc);

		if (detectedGrindingArea == null)
			detectedGrindingArea = data.getGrindingArea(loc);

		// Slimes are except from grinding due to their splitting nature
		if (!(event.getEntity() instanceof Slime) && mConfig.penaltyGrindingEnable
				&& !killed.hasMetadata("MH:reinforcement") && !AreaManager.isWhitelisted(killed.getLocation())) {
			if (detectedGrindingArea != null) {
				data.lastKillAreaCenter = null;
				data.setDampenedKills(detectedGrindingArea.count++);

				if (data.getDampenedKills() == 20)
					mAreaManager.registerKnownGrindingSpot(detectedGrindingArea);
			} else {
				if (data.lastKillAreaCenter != null) {
					if (loc.getWorld().equals(data.lastKillAreaCenter.getWorld())) {
						if (loc.distance(data.lastKillAreaCenter) < data.getcDampnerRange()) {
							data.setDampenedKills(data.getDampenedKills() + 1);
							if (data.getDampenedKills() == 10)
								data.recordGrindingArea();
						} else {
							data.lastKillAreaCenter = loc.clone();
							data.setDampenedKills(0);
						}
					} else {
						data.lastKillAreaCenter = loc.clone();
						data.setDampenedKills(0);
					}
				} else {
					data.lastKillAreaCenter = loc.clone();
					data.setDampenedKills(0);
				}
			}

			if (data.getDampenedKills() > 14) {
				if (data.getKillstreakLevel() != 0)
					killer.sendMessage(ChatColor.RED + Messages.getString("mobhunting.killstreak.lost"));
				data.setKillStreak(0);
			}
		}

		double cash = mConfig.getBaseKillPrize(killed);
		debug("Mob Basic Prize=%s", cash);
		double multiplier = 1.0;

		// Apply the modifiers
		ArrayList<String> modifiers = new ArrayList<String>();
		for (IModifier mod : mModifiers) {
			if (mod.doesApply(killed, killer, data, info, lastDamageCause)) {
				double amt = mod.getMultiplier(killed, killer, data, info, lastDamageCause);
				if (amt != 1.0) {
					modifiers.add(mod.getName());
					multiplier *= amt;
					data.addModifier(mod.getName(), amt);
				}
			}
		}
		data.setReward(cash);

		multiplier *= data.getKillstreakMultiplier();

		String extraString = "";

		// Only display the multiplier if its not 1
		if (Math.abs(multiplier - 1) > 0.05)
			extraString += String.format("x%.1f", multiplier);

		// Add on modifiers
		for (String modifier : modifiers)
			extraString += ChatColor.WHITE + " * " + modifier;

		cash *= multiplier;

		if ((cash >= 0.01) || (cash <= -0.01)) {
			// TODO: This must be moved, only works for cash!=0
			MobHuntKillEvent event2 = new MobHuntKillEvent(data, info, killed, killer);
			Bukkit.getPluginManager().callEvent(event2);

			if (event2.isCancelled()) {
				debug("KillBlocked %s: MobHuntKillEvent was cancelled", killer.getName());
				return;
			}

			if (killed instanceof Player && killer instanceof Player) {
				if (CitizensCompat.isDisabledInConfig() || !CitizensCompat.isCitizensSupported()
						|| !CitizensCompat.isNPC(killed)) {
					if (mConfig.robFromVictim) {
						mEconomy.withdrawPlayer((Player) killed, cash);
						if (!killed_muted)
							killed.sendMessage(ChatColor.RED + "" + ChatColor.ITALIC
									+ Messages.getString("mobhunting.moneylost", "prize", mEconomy.format(cash)));
						debug("%s lost %s", killed.getName(), mEconomy.format(cash));
					}
				}
			}

			if (info.assister == null) {
				if (cash > 0) {
					mEconomy.depositPlayer(killer, cash);
					debug("%s got a reward (%s)", killer.getName(), mEconomy.format(cash));
				} else {
					mEconomy.withdrawPlayer(killer, -cash);

					debug("%s got a penalty (%s)", killer.getName(), mEconomy.format(cash));

				}

			} else {
				cash = cash / 2;
				if (cash > 0) {
					mEconomy.depositPlayer(killer, cash);
					onAssist(info.assister, killer, killed, info.lastAssistTime);
					debug("%s got a ½ reward (%s)", killer.getName(), mEconomy.format(cash));
				} else {
					mEconomy.withdrawPlayer(killer, -cash);
					onAssist(info.assister, killer, killed, info.lastAssistTime);
					debug("%s got a ½ penalty (%s)", killer.getName(), mEconomy.format(cash));
				}
			}

			// TODO: record mythicmob kills as its own kind of mobs
			if (ExtendedMobType.getExtendedMobType(killed) != null)
				getDataStore().recordKill(killer, ExtendedMobType.getExtendedMobType(killed),
						killed.hasMetadata("MH:hasBonus"));

			if (!killer_muted)
				if (extraString.trim().isEmpty()) {
					if (cash > 0) {
						killer.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
								+ Messages.getString("mobhunting.moneygain", "prize", mEconomy.format(cash)));
					} else {
						killer.sendMessage(ChatColor.RED + "" + ChatColor.ITALIC
								+ Messages.getString("mobhunting.moneylost", "prize", mEconomy.format(cash)));

					}
				} else
					killer.sendMessage(
							ChatColor.GREEN + "" + ChatColor.ITALIC + Messages.getString("mobhunting.moneygain.bonuses",
									"prize", mEconomy.format(cash), "bonuses", extraString.trim()));
		} else
			debug("KillBlocked %s: Gained money was less than 1 cent (grinding or penalties) (%s)", killer.getName(),
					extraString);

		// Run console commands as a reward
		if (data.getDampenedKills() < 10) {
			if (!mConfig.getKillConsoleCmd(killed).equals("") && mConfig.getCmdRunProbabilityBase(killed) != 0) {
				if (mRand.nextInt(mConfig.getCmdRunProbabilityBase(killed)) < mConfig.getCmdRunProbability(killed)) {
					String worldname = killer.getWorld().getName();
					String killerpos = killer.getLocation().getBlockX() + " " + killer.getLocation().getBlockY() + " "
							+ killer.getLocation().getBlockZ();
					String killedpos = killed.getLocation().getBlockX() + " " + killed.getLocation().getBlockY() + " "
							+ killed.getLocation().getBlockZ();
					String prizeCommand = mConfig.getKillConsoleCmd(killed).replaceAll("\\{player\\}", killer.getName())
							.replaceAll("\\{killed_player\\}", killed.getName())
							.replaceAll("\\{killer\\}", killer.getName()).replaceAll("\\{killed\\}", killed.getName())
							.replaceAll("\\{world\\}", worldname).replace("\\{prize\\}", mEconomy.format(cash))
							.replaceAll("\\{killerpos\\}", killerpos).replaceAll("\\{killedpos\\}", killedpos);
					debug("command to be run is:" + prizeCommand);
					if (!mConfig.getKillConsoleCmd(killed).equals("")) {
						String str = prizeCommand;
						do {
							if (str.contains("|")) {
								int n = str.indexOf("|");
								Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
										str.substring(0, n));
								str = str.substring(n + 1, str.length()).toString();
							}
						} while (str.contains("|"));
						Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), str);
					}
					// send a message to the player
					if (!mConfig.getKillRewardDescription(killed).equals("") && !killer_muted) {
						killer.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
								+ mConfig.getKillRewardDescription(killed).replaceAll("\\{player\\}", killer.getName())
										.replaceAll("\\{killed_player\\}", killed.getName())
										.replaceAll("\\{killer\\}", killer.getName())
										.replaceAll("\\{killed\\}", killed.getName())
										.replace("\\{prize\\}", mEconomy.format(cash))
										.replaceAll("\\{world\\}", worldname).replaceAll("\\{killerpos\\}", killerpos)
										.replaceAll("\\{killedpos\\}", killedpos));
					}
				}
			}
		}
	}

	private void onAssist(Player player, Player killer, LivingEntity killed, long time) {
		if (!mConfig.enableAssists || (System.currentTimeMillis() - time) > mConfig.assistTimeout * 1000)
			return;

		double multiplier = mConfig.assistMultiplier;
		double ks = 1.0;
		if (mConfig.assistAllowKillstreak)
			ks = Misc.handleKillstreak(player);

		multiplier *= ks;
		double cash = 0;
		if (killed instanceof Player)
			cash = mConfig.getBaseKillPrize(killed) * multiplier / 2;
		else
			cash = mConfig.getBaseKillPrize(killed) * multiplier;

		if (cash >= 0.01) {
			getDataStore().recordAssist(player, killer, ExtendedMobType.getExtendedMobType(killed),
					killed.hasMetadata("MH:hasBonus"));
			mEconomy.depositPlayer(player, cash);
			debug("%s got a on assist reward (%s)", player.getName(), mEconomy.format(cash));

			if (ks != 1.0)
				player.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
						+ Messages.getString("mobhunting.moneygain.assist", "prize", mEconomy.format(cash)));
			else
				player.sendMessage(ChatColor.GREEN + "" + ChatColor.ITALIC
						+ Messages.getString("mobhunting.moneygain.assist.bonuses", "prize", mEconomy.format(cash),
								"bonuses", String.format("x%.1f", ks)));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerJoin(PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		setHuntEnabled(player, true);
		if (player.hasPermission("mobhunting.update")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					UpdateHelper.pluginUpdateCheck(player, true, true);
				}
			}.runTaskLater(instance, 20L);
		}
		boolean learning_mode = getDataStore().getPlayerSettings(player).isLearningMode();
		boolean muted = getDataStore().getPlayerSettings(player).isMuted();
		if (muted)
			debug("%s isMuted()", player.getName());
		if (learning_mode)
			debug("%s is in LearningMode()", player.getName());

		addPlayerSettings(player, new PlayerSettings(player, learning_mode, muted));
		mStoreManager.createPlayerSettings(player, learning_mode, muted);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		mPlayerSettings.remove(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void bonusMobSpawn(CreatureSpawnEvent event) {
		if (CitizensCompat.isCitizensSupported() && CitizensCompat.isNPC(event.getEntity()))
			return;

		if (event.getEntityType() == EntityType.ENDER_DRAGON)
			return;

		if (!MobHuntingManager.isHuntEnabledInWorld(event.getLocation().getWorld())
				|| (mConfig.getBaseKillPrize(event.getEntity()) <= 0
						&& mConfig.getKillConsoleCmd(event.getEntity()).equals(""))
				|| event.getSpawnReason() != SpawnReason.NATURAL)
			return;

		if (mRand.nextDouble() * 100 < mConfig.bonusMobChance) {
			mParticleManager.attachEffect(event.getEntity(), Effect.MOBSPAWNER_FLAMES);
			if (mRand.nextBoolean())
				event.getEntity()
						.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 3));
			else
				event.getEntity().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));

			event.getEntity().setMetadata("MH:hasBonus", new FixedMetadataValue(this, true));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void spawnerMobSpawn(CreatureSpawnEvent event) {

		if (CitizensCompat.isCitizensSupported() && CitizensCompat.isNPC(event.getEntity()))
			return;

		if (!MobHuntingManager.isHuntEnabledInWorld(event.getLocation().getWorld())
				|| (mConfig.getBaseKillPrize(event.getEntity()) <= 0)
						&& mConfig.getKillConsoleCmd(event.getEntity()).equals(""))
			return;

		if (event.getSpawnReason() != SpawnReason.SPAWNER && event.getSpawnReason() != SpawnReason.SPAWNER_EGG)
			return;

		if (!mConfig.allowMobSpawners)
			event.getEntity().setMetadata("MH:blocked", new FixedMetadataValue(this, true));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void reinforcementMobSpawn(CreatureSpawnEvent event) {
		if (!MobHuntingManager.isHuntEnabledInWorld(event.getLocation().getWorld())
				|| (mConfig.getBaseKillPrize(event.getEntity()) <= 0)
						&& mConfig.getKillConsoleCmd(event.getEntity()).equals(""))
			return;

		if (event.getSpawnReason() == SpawnReason.REINFORCEMENTS)
			event.getEntity().setMetadata("MH:reinforcement", new FixedMetadataValue(this, true));
	}

	/**
	 * Set if MobHunting is allowed for the player
	 * 
	 * @param player
	 * @param enabled
	 *            = true : means the MobHunting is allowed
	 */
	public void setHuntEnabled(Player player, boolean enabled) {
		player.setMetadata("MH:enabled", new FixedMetadataValue(MobHunting.instance, enabled));
	}

	/**
	 * Checks if the player has permission to kill the mob
	 * 
	 * @param player
	 * @param mob
	 * @return true if the player has permission to kill the mob
	 */
	public boolean hasPermissionToKillMob(Player player, LivingEntity mob) {
		String permission_prefix = "*";
		if (MythicMobsCompat.isSupported() && MythicMobsCompat.isMythicMob(mob)) {
			permission_prefix = MythicMobsCompat.getMythicMobType(mob);
			if (player.isPermissionSet("mobhunting.mobs." + permission_prefix))
				return player.hasPermission("mobhunting.mobs." + MythicMobsCompat.getMythicMobType(mob));
			else {
				MobHunting.debug("Permission mobhunting.mobs.mythicmobtype not set, defaulting to True.");
				return true;
			}
		} else if (CitizensCompat.isCitizensSupported() && CitizensCompat.isSentry(mob)) {
			permission_prefix = "npc-" + CitizensCompat.getNPCId(mob);
			if (player.isPermissionSet("mobhunting.mobs." + permission_prefix))
				return player.hasPermission("mobhunting.mobs." + permission_prefix);
			else {
				MobHunting.debug("Permission mobhunting.mobs.'" + permission_prefix + "' not set, defaulting to True.");
				return true;
			}
		} else {
			permission_prefix = mob.getType().toString();
			if (player.isPermissionSet("mobhunting.mobs." + permission_prefix))
				return player.hasPermission("mobhunting.mobs." + permission_prefix);
			else {
				MobHunting.debug("Permission 'mobhunting.mobs.*' or 'mobhunting.mobs." + permission_prefix
						+ "' not set, defaulting to True.");
				return true;
			}
		}
	}

	/**
	 * Returns the MobHunting object
	 *
	 * @return the instance
	 */
	public static MobHunting getInstance() {
		return instance;
	}

	/**
	 * Gets the MobHuntingHandler
	 * 
	 * @return MobHuntingManager
	 */
	public MobHuntingManager getMobHuntingManager() {
		return mMobHuntingManager;
	}

	/**
	 * Gets the DamageInformation for a LivingEntity
	 * 
	 * @param entity
	 * @return
	 */
	public DamageInformation getDamageInformation(LivingEntity entity) {
		return mDamageHistory.get(entity);
	}

	/**
	 * Get all Achievements for all players.
	 * 
	 * @return
	 */
	public AchievementManager getAchievements() {
		return mAchievementManager;
	}

	/**
	 * Gets the Database Store Manager
	 * 
	 * @return
	 */
	public DataStoreManager getDataStore() {
		return mStoreManager;
	}

	/**
	 * Gets the LeaderboardManager
	 * 
	 * @return
	 */
	public LeaderboardManager getLeaderboardManager() {
		return mLeaderboardManager;
	}

	/**
	 * Get playerSettings from memory
	 * 
	 * @param player
	 * @return PlayerSettings
	 */
	public PlayerSettings getPlayerSettings(Player player) {
		return mPlayerSettings.get(player.getUniqueId());
	}

	/**
	 * Store playerSettings in memory
	 * 
	 * @param playerSettings
	 */
	public void addPlayerSettings(Player player, PlayerSettings playerSettings) {
		mPlayerSettings.put(player.getUniqueId(), playerSettings);
	}

	// ************************************************************************************
	// SPONGE PROJECT
	// ************************************************************************************

	// private Logger logger;

	// @Plugin(id = "mobhuntingSponge", name = "MobHunting Project", version =
	// "1.0")
	// public class MobHuntingProject implements Listener {
	// @Subscribe
	// public void onServerStart(ServerStartedEvent event) {
	// Hey!The server has started!
	// Try instantiating your logger in here.
	// (There's a guide for that)
	// logger.info("Hello World!");
	// }

	// @Subscribe
	// public void onServerStop(ServerStoppedEvent event) {
	// Hey! The server has started!
	// Try instantiating your logger in here.
	// (There's a guide for that)
	// logger.info("Goodbye World!");
	// }
	// }

	// ************************************************************************************

}
