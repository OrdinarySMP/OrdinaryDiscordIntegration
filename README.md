# Discord: Just Sync

## Features
- Send minecraft chat messages to discord
- Send discord messages to the minecraft chat
- Webhook support
- Commands, on discord and ingame
- Death and advancement messages
- whitelist through discord roles

## Config
The config file is located in `<minecraft-folder>/config/discord-js/discord-justsync.toml`,
it will be created on first launch.
To use the mod you must configure `botToken` with you discord bot token and 
`serverChatChannel` with the id of the channel you want to use for the minecraft chat channel.
Without doing so minecraft will fail to start.

## The Discord Bot
You will have to create a Discord Bot by creating a new Application in the Discord Developer Portal.
Enable the `SERVER MEMBERS INTENT` and the `MESSAGE CONTENT INTENT` in the bot section of your Application
Then invite it to your discord server with Permissions to:

- send and read messages
- modify webhooks
- manage nicknames (optional)
- manage roles (optional)

Do that by going to the OAuth2 section's Url Generator, selecting `bot`, then selecting all required permissions or `Administrator`.
Then copy the link and paste it in your browser and add the bot to your server.

After you have done so you will be able to copy the bot's token (from the Bot Section of your Application) to put it in your config folder.
Never reveal this token to anyone as it gives full control over the bot.

## Linking
By enabling the `linking.enableLinking` option (default: true) players will be required to link their minecraft account to a discord account.
You can control which players can join by configuring `linking.requiredRoles` with a role id required to join the server.
By default, a player's discord account will be renamed to their ingame name whenever they join (`linking.renameOnJoin`).
For this to work the `manage roles` permission is required.

## Commands
### Minecraft
- `/discord reload` reload the configs
- `/discord unlink` remove the link of your minecraft account
- `/discord unlink <player>` remove a link from a discord user to a minecraft account (op required)
### Discord
- `/link <code>` used to link to your minecraft account
- `/linking get` query link data for yourself
- `/linking get <player>` query link data for other players (moderate member permission required) 
- `/linking unlink` unlink yourself
- `/linking unlink <player>` unlink a player (moderate members permission required)
- `/list` List the currently online players
- `/reload` reload the config file (admin only)
### Console
You can configure commands to be run in minecraft in the config under `commands.commandList`.
These commands can be run by any admin or user with the `commands.opRole`.
Commands can only be run from the `commands.consoleChannel`.
By default, there are these 4 commands: `stop`, `kill`, `kick`, `ban` but you can configure more of remove them.
The default command prefix is `//` so you can for example type `//kick Notch` to kick Notch from the minecraft server.

## Integrations
### [Vanish](https://modrinth.com/mod/vanish)
This mod has an integration for melius's vanish mod, so that this mod does not reveal vanished players.

### [Luck Perms](https://modrinth.com/plugin/luckperms)
This mod can add alt accounts to a luckperms group

## Problems and Suggestions
If you encounter any problems or bugs or have a suggestion for this mod feel free to open an Issue on our GitHub repository.
