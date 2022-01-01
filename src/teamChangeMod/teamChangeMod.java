package teamChangeMod;

import mindustry.game.*;
import mindustry.game.Team.*;
import mindustry.game.Teams.*;
import mindustry.game.EventType.*;
import mindustry.mod.Plugin.*;
import mindustry.gen.*;
import mindustry.mod.*;
import arc.Core.*;
import arc.util.*;
import arc.util.Log;
import arc.math.*;
import arc.struct.Seq;
import arc.Events;

import static mindustry.Vars.*;

public class teamChangeMod extends Plugin{
    public static float teamChangeTolerance = 1f;
    public static Team adminTeam = null;

    @Override
    public void init(){
        Events.on(PlayerJoin.class, e -> {
            e.player.team(getAssignTeam(e.player));
        });
        Events.on(GameOverEvent.class, e -> {
            adminTeam = null;
        });
    }
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("team", "[team]", "Change team, use without arguments to show available teams.", (args, player) -> {
            try{
                if(args.length == 1){
                    TeamData newTeam = state.teams.active.find(t -> {
                        return t.team.name.equalsIgnoreCase(args[0]);
                    });
                    if(newTeam != null){
                        if(state.rules.pvp && newTeam.team != Team.derelict && newTeam.team != adminTeam && !(state.rules.waves && newTeam.team == state.rules.waveTeam)){
                            int[] newTeamPlayers = new int[]{0};
                            Groups.player.each(p -> {
                                if(p.team() == newTeam.team){
                                    newTeamPlayers[0]++;
                                };
                            });
                            if(newTeamPlayers[0] / Groups.player.size() * state.teams.active.size <= teamChangeTolerance){
                                player.clearUnit();
                                player.team(newTeam.team);
                                Call.sendMessage("Player " + player.name + " [white]changed team to [#" + newTeam.team.color + "]" + newTeam.team.name + "[].");
                            }else{
                                player.sendMessage("That team has too much players.");
                            };
                        }else{
                            player.sendMessage("You can't switch to that team.");
                        };
                    }else{
                        player.sendMessage("Couldn't find that team.");
                    };
                }else{
                    String[] teams = new String[]{""};
                    state.teams.active.each(t -> {
                        teams[0] += "[#" + t.team.color + "]" + t.team.name + ", ";
                    });
                    player.sendMessage(teams[0]);
                };
            }catch(Exception badArguments){
                player.sendMessage("Something went wrong.");
            };
        });
        handler.<Player>register("forceteam", "[team] [player...]", "Change team forcibly, usable by admins only. Switches typer if target not provided.", (args, player) -> {
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
                                    switchPlayer.sendMessage("An admin has changed your team to [#" + newTeam.color + "]" + newTeam.name + "[].");
                                    player.sendMessage("Changed team of player " + switchPlayer.name + " [white]to [#" + newTeam.color + "]" + newTeam.name + "[].");
                                }else{
                                    player.sendMessage("Couldn't find that player.");
                                };
                            }else{
                                player.clearUnit();
                                player.team(newTeam);
                                player.sendMessage("Changed team to [#" + newTeam.color + "]" + newTeam.name +"[].");
                            };
                        }else{
                            player.sendMessage("Couldn't find that team.");
                        };
                    }else{
                        String[] teams = new String[]{""};
                        for(Team t : Team.all){
                            teams[0] += "[#" + t.color + "]" + t.name + ", ";
                        };
                        player.sendMessage(teams[0]);
                    };
                }else{
                    player.sendMessage("You have to be admin to use this command.");
                };
            }catch(Exception badArguments){
                player.sendMessage("Something went wrong.");
            };
        });
        handler.<Player>register("spectate", "Spectate the game. Use while spectating to stop spectating.", (args, player) -> {
            if(player.team().active() || player.unit().isNull()){
                Team newTeam = null;
                for(Team t : Team.all){
                    if(t.core() == null){
                        newTeam = t;
                    };
                };
                if(newTeam != null){
                    player.clearUnit();
                    player.team(newTeam);
                    Call.sendMessage(player.name + " [white]is now spectating.");
                }else{
                    player.sendMessage("Couldn't find a suitable team to switch to in order to spectate. Wait for any team to lose.");
                };
            }else{
                player.team(getAssignTeam(player));
                Call.sendMessage(player.name + " [white]is no longer spectating.");
            };
        });
        handler.<Player>register("forceteamassign", "[team]" ,"Force all admins to be on one team and all players to be on other teams. Input \"reset\" to cancel forceteamassign.", (args, player) -> {
            try{
                if(player.admin){
                    Team newTeam = null;
                    if(args.length == 1){
                        if(!args[0].equalsIgnoreCase("reset")){
                            for(Team t : Team.all){
                                if(t.name.equalsIgnoreCase(args[0])){
                                    newTeam = t;
                                };
                            };
                            if(newTeam == null){
                                player.sendMessage("Couldn't find that team.");
                            };
                        }else{
                            player.sendMessage("Resetting forced team.");
                        };
                    }else{
                        newTeam = player.team();
                    };
                    if(newTeam != null || args[0].equalsIgnoreCase("reset")){
                        adminTeam = newTeam;
                    };
                    if(adminTeam != null){
                        Groups.player.each(p -> {
                            Team assignTeam = getAssignTeam(p);
                            p.clearUnit();
                            p.team(assignTeam);
                        });
                        player.sendMessage("Teams forced from [#" + adminTeam.color + "]" + adminTeam.name +"[].");
                    };
                }else{
                    player.sendMessage("You have to be admin to use this command.");
                };
            }catch(Exception badArguments){
                player.sendMessage("Something went wrong.");
            };
        });
    }
    public void registerServerCommands(CommandHandler handler){
        handler.register("teamchangetolerance", "<tolerance>" ,"Changes how tolerant towards team player counts is the team change plugin. Lower = more tolerant. Default is 10.", (args) -> {
            try{
                float newTolerance = Float.parseFloat(args[0]);
                teamChangeTolerance = newTolerance;
                Log.info("Changed team change tolerance to " + String.valueOf(newTolerance));
            }catch(Exception badArgument){
                Log.info("Bad argument.");
            };
        });
    }
    public Team getAssignTeam(Player p){
        if(state.rules.pvp){
            return state.teams.active.min(data -> {
                if(p.admin && data.team == adminTeam){
                    return -1;
                };
                if(data.team == Team.derelict || data.team == adminTeam || (state.rules.waves && data.team == state.rules.waveTeam)){
                    return Integer.MAX_VALUE;
                };
                int teamPlayers = 0;
                for(Player pl : Groups.player){
                    if(pl != p && pl.team() == data.team){
                        teamPlayers++;
                    };
                };
                return teamPlayers;
            }).team;
        };
        return state.rules.defaultTeam;
    }
}
