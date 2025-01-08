# Features
- Send minecraft chat messages to discord
- Send discord messages to the minecraft chat
- Webhook support
- Commands, on discord and ingame
- Death and advancement messages

# Config
The config file is located in `<minecraft-folder>/config/discord-js/discord-justsync.toml`,
it will be created on first launch.
To use the mod you must configure `botToken` with you discord bot token and 
`serverChatChannel` with the id of the channel you want to use for the minecraft chat channel.
Without doing so minecraft will fail to start.

# The Discord Bot
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
