Forsaken Creatures is a high-stakes survival mod that transforms ordinary creatures into formidable, boss-like entities when they kill a player. These "Forsaken" beasts grow significantly in size, emit ominous visual effects, and relentlessly stalk players across the map.

The Transformation
- Visuals: Forsaken creatures grow 10x their normal size and are marked by a red beacon visible from afar. As you approach, the beacon shifts into crackling lightning, and a trail of dark particles and embers follows their every step.
- The Hunt: Forsaken creatures are programmed to stalk the nearest player. They can teleport closer if their target is too far away, but they will never jump directly onto deeded land or land too close to a player (maintaining a 35-tile buffer).
- Targeted Alerts: Players being hunted will receive an ominous notification: "You feel a chill down your spine... [Name] is actively hunting you!"

Progression & Rewards
- Leveling System: Every subsequent player kill levels the creature up (up to Level 10), granting it additional stat points and increasing its lethality.
- Inventory Rewards: Upon becoming Forsaken and each time they level up, creatures are granted configurable items in their inventory. When they level up, old level items are consumed and replaced with more powerful rewards.
- Victory Bounty: Defeating a Forsaken awards the victor with a cash bounty that scales with the creature's level.
- Grand Celebration: Slaying a Forsaken triggers a server-wide event. "Xmas lights" will now appear on every deed token across the entire server globally, regardless of player distance. Colorful fireworks display within a 200-tile radius of each deed to celebrate the victory.

Fair Play & Robust Rewards
- Pet & Charmed Attribution: Kills by pets or charmed creatures are correctly attributed to their owner, ensuring they receive the full coin bounty.
- Smart Looting: Reward items are automatically moved to the creature's corpse. If a corpse isn't found immediately, the mod retries for 30 seconds before safely dropping items on the ground where the creature died.
- Guard & Admin Safeguards: Participation by guards or spirit guards in a kill will correctly deny the bounty and inventory rewards. Similarly, administrator-triggered deaths (WIZKILL) do not award bounties to bystanders, and rewards are deleted rather than dropped.
- Persistent Daily Limit: The daily spawn count is fully persistent and survives server restarts/downtime, resetting only when the real-world 24-hour window has passed.
- Player Retention & Opt-In: Server owners can require players to explicitly opt-in to Forsaken hunts using /forsaken on. Players who haven't opted in or have opted out (/forsaken off) will not be targeted or hunted.
- Skill Retention: A configurable skill retention system can restore a portion of lost skills when a player dies. Use enable_skill_retention as a master switch, and then toggle retain_on_forsaken_death and/or retain_on_any_creature_death to specify which types of deaths qualify. The retention percentage scales dynamically with the player's Fighting Skill.
- Skill/Power Gating: Admins can set a minimum Fighting Skill requirement (MinimumFSREQ) to protect newer or less combat-focused players from being targeted by Forsaken creatures.

The Life Cycle
A Forsaken creature remains active until:
  1. It is killed by a player.
  2. It reaches 10 kills(or whatever is set), at which point it satisfies its bloodlust and reverts to its original, normal state.

Admin Control
The mod is highly configurable via forsaken.config, allowing administrators to adjust:
- Spawn chances and daily limits.
- Admin command power level requirement.
- Teleportation logic and hunting ranges.
- Stat gains and bounty amounts.
- Specific inventory rewards per level (with rarity, material, and quantity support).
- Trail effect visuals and particle types.

Player Commands:
- /forsaken on - Opt-in to Forsaken creature hunts (if enable_opt_in is true).
- /forsaken off - Opt-out of Forsaken creature hunts.

Admin Commands (Configurable Power Level):
- /forsaken list - Lists all currently active Forsaken creatures and their locations.
- /forsaken spawn <TemplateName> - Manually spawn a Forsaken creature at your position.
- /forsaken level <Level> - Set the Forsaken level (1-10) of the targeted creature.
- /forsaken clear - Reverts all active Forsaken to their normal states.
- /forsaken celebrate - Triggers a manual server-wide celebration.
- /forsaken reload - Reloads the configuration file without a server restart.
- /forsaken status - Shows the current mod status, reward configurations, and daily spawn counts.
- /forsaken resetlimits - Resets the daily spawn counter and 24-hour timer.
- /forsaken announcements - Toggles the world-wide announcements for non-admin players.
- /forsaken verbose - Toggles the verbose debug mode on the run.
- /forsaken help (or .) - Shows the help message.