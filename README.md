# Screen Sahre/Cast to browser

**This is a simple implementation of Android's MediaProjectin API and web-socket .**

## Features

- [✓]  Supports Video 
- [✓]  Supprots Audio(mic)
- [✓]  Play/pause (Audio/video both)
- [✓]  Low latency
- [✓]  Diffrent FPS support
- [✓]  Noice and Echo cancelation
> [x]  Both Way Streaming

## How to :
> Setup Server

**Clone this repo and run [server.js](./Server/server.js) using nodejs. This opens a web-socket server on port `8080` and an http server(for browser requsts) on port `3000`**



> Android client

**Build and run the apk on your android device**

**After all this set up, acquire the ipv4 address of your server machine. Then connect to then server using the acquired ip-address, The url should be in this formate `ws://ip_address:8080` and you are ready to screen-share your android device**

>Browser client/viewer

**Open `http://localhost:3000` or `http://ip-address:3000` in browser and click `Play Video` , `Play Audio` buttons accordingly.**

> [!TIP]
> To get ip-address of your machine enter this command in the terminal
> > windows :

> `ipconfig`
>>linux | mac :

>`ifconfig`
