# StopSpyingOnYourPlayersMessages
Chat plugin developers often overlook a simple yet crucial aspect: privacy. 
Every private message sent by players is logged in the console—and, in the worst-case scenario, also recorded by plugins like CoreProtect. 

The goal of this project is to put an end to this mess.

## How it works?
The project works very simply: it intercepts packets from a player's connection and processes them itself. 
The end-point plugins receive the command exactly as it was sent, but it does not appear in the logs.

> [!CAUTION]
> This also causes the [PlayerCommandPreprocessEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerCommandPreprocessEvent.html) to stop being fired.

## Use as a plugin
There is Paper plugin that I distribute myself. 
It performs a single task - it triggers its logic as soon as a player joins the server.\
By default, it stops those commands from being logged: `tell`, `msg`, `m`, `w`, `r` and `reply`.

# Use as an API
```groovy
maven {
    name = "modoruReleases"
    url = uri("https://repository.modoru.fun/releases")
}
```

```groovy
implementation("fun.lofe:stop-spying-on-your-players-messages:<version>")
```