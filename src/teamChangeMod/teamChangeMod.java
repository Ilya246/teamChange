package teamChangeMod;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Structs;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

import static mindustry.Vars.netServer;
import static mindustry.Vars.state;

public class teamChangeMod extends Plugin {
    public static float teamChangeTolerance = 1f;
    public static Team adminTeam = null;

    @Override
    public void init() {
        netServer.assigner = (player, players) -> {
            if (state.rules.pvp) {
                return state.teams.active.min(data -> {
                    if (player.admin && data.team == adminTeam) {
                        return -1;
                    }
                    if (data.team == Team.derelict || data.team == adminTeam || (state.rules.waves && data.team == state.rules.waveTeam)) {
                        return Integer.MAX_VALUE;
                    }
                    int teamPlayers = 0;
                    for (Player pl : Groups.player) {
                        if (pl != player && pl.team() == data.team) {
                            teamPlayers++;
                        }
                    }
                    return teamPlayers;
                }).team;
            }
            return state.rules.defaultTeam;
        };
        Events.on(GameOverEvent.class, e -> adminTeam = null);
    }

    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("team", "[team]", "Change team, use without arguments to show available teams.", (args, player) -> {
            try {
                if (args.length == 1) {
                    Team newTeam = findTeam(args[0]);
                    if (newTeam != null && newTeam.active()) {
                        if (state.rules.pvp && newTeam != Team.derelict && newTeam != adminTeam && !(state.rules.waves && newTeam == state.rules.waveTeam)) {
                            float newTeamPlayers = Groups.player.count(p -> p.team() == newTeam);

                            if (newTeamPlayers / Groups.player.size() * state.teams.active.size <= teamChangeTolerance) {
                                player.clearUnit();
                                player.team(newTeam);
                                Call.sendMessage("[accent]Player " + player.coloredName() + "[accent] changed team to " + colorizedTeam(newTeam) + "[].");
                            } else {
                                player.sendMessage("[scarlet]That team has too much players.");
                            }
                        } else {
                            player.sendMessage("[scarlet]You can't switch to that team.");
                        }
                    } else {
                        player.sendMessage("[scarlet]Couldn't find that team.");
                    }
                } else {
                    StringBuilder teams = new StringBuilder();
                    Structs.each(t -> teams.append("\n[gold] - [white]").append(colorizedTeam(t)), Team.baseTeams);
                    player.sendMessage("[scarlet]Team not found!\n[accent]Available teams:[white]" + teams);
                }
            } catch (Exception badArguments) {
                player.sendMessage("[scarlet]Something went wrong.");
            }
        });
        handler.<Player>register("forceteam", "[team] [player...]", "Change team forcibly, usable by admins only. Switches typer if target not provided.", (args, player) -> {
            try {
                if (player.admin) {
                    if (args.length >= 1) {
                        Team newTeam = findTeam(args[0]);
                        if (newTeam != null) {
                            if (args.length == 2) {
                                Player switchPlayer = Groups.player.find(p -> Strings.stripColors(p.name).toLowerCase().contains(args[1]) || p.id == Strings.parseInt(args[1]));
                                if (switchPlayer != null) {
                                    switchPlayer.clearUnit();
                                    switchPlayer.team(newTeam);
                                    switchPlayer.sendMessage("[accent]An admin has changed your team to " + colorizedTeam(newTeam) + "[].");
                                    player.sendMessage("[accent]Changed team of player " + switchPlayer.coloredName() + "[accent] to " + colorizedTeam(newTeam) + "[].");
                                } else {
                                    player.sendMessage("[scarlet]Couldn't find that player.");
                                }
                            } else {
                                player.clearUnit();
                                player.team(newTeam);
                                player.sendMessage("[accent]Changed team to " + colorizedTeam(newTeam) + "[].");
                            }
                        } else {
                            player.sendMessage("[scarlet]Couldn't find that team.");
                        }
                    } else {
                        StringBuilder teams = new StringBuilder();
                        Structs.each(t -> teams.append("\n[gold] - [white]").append(colorizedTeam(t)), Team.baseTeams);
                        player.sendMessage("[scarlet]Team not found!\n[accent]Available teams:[white]" + teams);
                    }
                } else {
                    player.sendMessage("[scarlet]You have to be admin to use this command.");
                }
            } catch (Exception badArguments) {
                player.sendMessage("[scarlet]Something went wrong.");
            }
        });
        handler.<Player>register("spectate", "Spectate the game. Use while spectating to stop spectating.", (args, player) -> {
            if (player.team().active() || player.unit().isNull()) {
                Team newTeam = Structs.find(Team.all, t -> t.data().noCores());
                if (newTeam != null) {
                    player.clearUnit();
                    player.team(newTeam);
                    Call.sendMessage(player.coloredName() + " [accent]is now spectating.");
                } else {
                    player.sendMessage("[scarlet]Couldn't find a suitable team to switch to in order to spectate. Wait for any team to lose.");
                }
            } else {
                player.team(netServer.assignTeam(player));
                Call.sendMessage(player.coloredName() + " [accent]is no longer spectating.");
            }
        });
        handler.<Player>register("forceteamassign", "[team]", "Force all admins to be on one team and all players to be on other teams. Input \"reset\" to cancel forceteamassign.", (args, player) -> {
            try {
                if (player.admin) {
                    Team newTeam = null;
                    if (args.length == 1) {
                        if (!args[0].equalsIgnoreCase("reset")) {
                            newTeam = findTeam(args[0]);
                            if (newTeam == null) {
                                player.sendMessage("[scarlet]Couldn't find that team.");
                            }
                        } else {
                            player.sendMessage("[accent]Resetting forced team.");
                        }
                    } else {
                        newTeam = player.team();
                    }
                    if (newTeam != null || args[0].equalsIgnoreCase("reset")) {
                        adminTeam = newTeam;
                    }
                    if (adminTeam != null) {
                        Groups.player.each(p -> {
                            Team assignTeam = netServer.assignTeam(p);
                            p.clearUnit();
                            p.team(assignTeam);
                        });
                        player.sendMessage("[accent]Teams forced from " + colorizedTeam(adminTeam) + "[].");
                    }
                } else {
                    player.sendMessage("[scarlet]You have to be admin to use this command.");
                }
            } catch (Exception badArguments) {
                player.sendMessage("[scarlet]Something went wrong.");
            }
        });
    }

    public void registerServerCommands(CommandHandler handler) {
        handler.register("teamchangetolerance", "<tolerance>", "Changes how tolerant towards team player counts is the team change plugin. Lower = more tolerant. Default is 1.", (args) -> {
            float newTolerance = Strings.parseFloat(args[0], 1f);
            teamChangeTolerance = newTolerance;
            Log.info("Changed team change tolerance to " + newTolerance);
        });
    }

    public static String colorizedTeam(Team team) {
        return "[#" + team.color + "]" + team.name;
    }

    public static Team findTeam(String name) {
        return Strings.canParseInt(name) ? Team.get(Strings.parseInt(name)) : Structs.find(Team.all, team -> team.name.equalsIgnoreCase(name));
    }
}
