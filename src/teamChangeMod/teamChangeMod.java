package teamChangeMod;

import arc.Core;
import arc.Events;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.Team.*;
import mindustry.game.Teams.*;
import mindustry.game.EventType.*;
import mindustry.mod.Plugin.*;
import mindustry.gen.*;
import mindustry.mod.*;

import static mindustry.Vars.*;

public class teamChangeMod extends Plugin{
    public static String configPrefix = "team-";

    public enum Config{
        allowSpectate("Whether to allow spectating.", true),
        teamChangeTolerance("Tolerance towards changing team to teams with more players. Higher = more tolerant.", 1.2f);

        public static final Config[] all = values();

        public final Object defaultValue;
        public String description;

        Config(String description, Object value){
            this.description = description;
            this.defaultValue = value;
            if(!Core.settings.has(getName())) set(value);
        }
        public String getName(){
            return configPrefix + name();
        }
        public float f(){
            return Core.settings.getFloat(getName(), (float)defaultValue);
        }
        public boolean b(){
            return Core.settings.getBool(getName(), (boolean)defaultValue);
        }
        public String s(){
            return Core.settings.get(getName(), defaultValue).toString();
        }
        public void set(Object value){
            Core.settings.put(getName(), value);
        }
    }

    @Override
    public void init(){
        netServer.assigner = (p, players) -> {
            if(state.rules.pvp){
                ObjectIntMap<Team> teamPlayers = new ObjectIntMap<>();
                for(Player pl : players){
                    teamPlayers.put(pl.team(), teamPlayers.get(pl.team(), 0) + 1);
                }
                return state.teams.active.copy().shuffle().min(data -> {
                    if(data.team == Team.derelict || data.cores.isEmpty()){
                        return Integer.MAX_VALUE;
                    };
                    return teamPlayers.get(data.team);
                }).team;
            };
            return state.rules.defaultTeam;
        };
    }
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("team", "[team]", "Change team, use without arguments to show available teams.", (args, player) -> {
            try{
                if(args.length == 1){
                    TeamData newTeam = state.teams.active.find(t -> {
                        return t.team.name.equalsIgnoreCase(args[0]);
                    });
                    if(newTeam != null){
                        if(state.rules.pvp && newTeam.team != Team.derelict && !newTeam.cores.isEmpty()){
                            int newTeamPlayers = 1; // intentionally set to 1 and not 0 to account for new player
                            for(Player p : Groups.player){
                                if(p.team() == newTeam.team){
                                    newTeamPlayers++;
                                };
                            }
                            if(newTeamPlayers / Groups.player.size() * state.teams.active.size <= Config.teamChangeTolerance.f()){
                                player.clearUnit();
                                player.team(newTeam.team);
                                Call.sendMessage("[accent]Player " + player.coloredName() + " [accent]changed team to [#" + newTeam.team.color + "]" + newTeam.team.name + "[].");
                            }else{
                                player.sendMessage("[scarlet]That team has too much players.");
                            };
                        }else{
                            player.sendMessage("[scarlet]You can't switch to that team.");
                        };
                    }else{
                        player.sendMessage("[scarlet]Couldn't find that team.");
                    };
                }else{
                    String[] teams = new String[]{""};
                    state.teams.active.each(t -> {
                        teams[0] += "[#" + t.team.color + "]" + t.team.name + ", ";
                    });
                    player.sendMessage(teams[0]);
                };
            }catch(Exception badArguments){
                player.sendMessage("[scarlet]Something went wrong.");
            };
        });
        handler.<Player>register("forceteam", "[team] [player...]", "Change team forcibly. Switches user if target not provided. [red]Admin only.", (args, player) -> {
            try{
                if(player.admin){
                    if(args.length >= 1){
                        Team newTeam = null;
                        for(Team t : Team.all){
                            if(t.name.equalsIgnoreCase(args[0])){
                                newTeam = t;
                            };
                        };
                        if(newTeam != null){
                            if(args.length == 2){
                                String[] switchName = new String[]{""};
                                for(int i = 1; i < args.length; i++){
                                    switchName[0] += args[i];
                                };
                                Player switchPlayer = Groups.player.find(p -> {
                                    return Strings.stripColors(p.name).toLowerCase().contains(switchName[0]) || p.id == Integer.parseInt(args[1]);
                                });
                                if(switchPlayer != null){
                                    switchPlayer.clearUnit();
                                    switchPlayer.team(newTeam);
                                    switchPlayer.sendMessage("[accent]An admin has changed your team to [#" + newTeam.color + "]" + newTeam.name + "[].");
                                    player.sendMessage("[accent]Changed team of player " + switchPlayer.name + " [white]to [#" + newTeam.color + "]" + newTeam.name + "[].");
                                }else{
                                    player.sendMessage("[scarlet]Couldn't find that player.");
                                };
                            }else{
                                player.clearUnit();
                                player.team(newTeam);
                                player.sendMessage("[accent]Changed team to [#" + newTeam.color + "]" + newTeam.name +"[].");
                            };
                        }else{
                            player.sendMessage("[scarlet]Couldn't find that team.");
                        };
                    }else{
                        String[] teams = new String[]{""};
                        for(Team t : Team.all){
                            teams[0] += "[#" + t.color + "]" + t.name + ", ";
                        };
                        player.sendMessage(teams[0]);
                    };
                }else{
                    player.sendMessage("[scarlet]You have to be admin to use this command.");
                };
            }catch(Exception badArguments){
                player.sendMessage("[scarlet]Something went wrong.");
            };
        });
        handler.<Player>register("spectate", "Spectate the game. Use while spectating to stop spectating.", (args, player) -> {
            if(!Config.allowSpectate.b()){
                player.sendMessage("[scarlet]Spectating is disabled on this server.");
                return;
            }
            if(player.team().active() || !player.unit().isNull()){
                Team newTeam = null;
                for(Team t : Team.all){
                    if(t.core() == null){
                        newTeam = t;
                        break;
                    };
                };
                if(newTeam != null){
                    player.clearUnit();
                    player.team(newTeam);
                    Call.sendMessage(player.coloredName() + " [accent]is now spectating.");
                }else{
                    player.sendMessage("[scarlet]Couldn't find a suitable team to switch to in order to spectate. Wait for any team to lose.");
                };
            }else{
                player.team(netServer.assignTeam(player));
                Call.sendMessage(player.coloredName() + " [accent]is no longer spectating.");
            };
        });
    }
    public void registerServerCommands(CommandHandler handler){
        handler.register("teamconfig", "[name] [value]", "Configure teamChange plugin settings. Run with no arguments to list values.", args -> {
            if(args.length == 0){
                Log.info("All config values:");
                for(Config c : Config.all){
                    Log.info("&lk| @: @", c.name(), "&lc&fi" + c.s());
                    Log.info("&lk| | &lw" + c.description);
                    Log.info("&lk|");
                }
                return;
            }
            try{
                Config c = Config.valueOf(args[0]);
                if(args.length == 1){
                    Log.info("'@' is currently @.", c.name(), c.s());
                }else{
                    if(args[1].equals("default")){
                        c.set(c.defaultValue);
                    }else{
                        try{
                            if(c.defaultValue instanceof Float){
                                c.set(Float.parseFloat(args[1]));
                            }else{
                                c.set(Boolean.parseBoolean(args[1]));
                            }
                        }catch(NumberFormatException e){
                            Log.err("Not a valid number: @", args[1]);
                            return;
                        }
                    }
                    Log.info("@ set to @.", c.name(), c.s());
                    Core.settings.forceSave();
                }
            }catch(IllegalArgumentException e){
                Log.err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.", args[0]);
            }
        });
    }
}
