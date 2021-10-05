import { app, BrowserWindow, KeyboardInputEvent, MouseInputEvent, MouseWheelInputEvent } from 'electron';
import net from 'net';
import path from 'path';
import { BrowseEvent, ResizeEvent } from './events';

app.disableHardwareAcceleration();

//let client = net.connect(9090, 'localhost');
let client = net.connect('\\\\.\\pipe\\ipcsockettest');
client.setEncoding('utf8');

app.on('ready', () => {
	console.log('Started Electron');

	const win = new BrowserWindow({
		width: 800,
		height: 600,
		show: false,
		frame: false,
		//transparent: true,
		webPreferences: { offscreen: true }
	});

	//win.webContents.beginFrameSubscription(true ,(image, dirtyRect) => {
	win.webContents.on('paint', (event, dirtyRect, image) => {
		const imageBytes: Uint8Array = image.crop(dirtyRect).toJPEG(100).valueOf();
		const command: string = ('paint:' + dirtyRect.x + ',' + dirtyRect.y + ',' + imageBytes.length).padEnd(32, ',');
		client.write(command, 'utf-8');
		client.write(imageBytes);
	});

	win.webContents.on('cursor-changed', (event, type) => {
		const command: string = ('cursor:' + type).padEnd(32, ',');
		client.write(command, 'utf-8');
	});

	win.webContents.setWindowOpenHandler(() => {
		return {
			action: 'allow',
			overrideBrowserWindowOptions: {
				autoHideMenuBar: true
			}
		}
	});

	win.webContents.setFrameRate(60);
	//win.focusOnWebView();

	win.loadFile(path.join(__dirname + '/index.html'));
	//win.loadURL('https://github.com');
	//win.loadURL('https://www.google.de');
	//win.loadURL('https://www.youtube.com');

	//win.webContents.openDevTools(); // do not work in offscreen mode

	//net.createServer(client => {
	client.on('data', data => {
		//console.log('----------\n' + data.toString('utf8'));
		data.toString('utf-8').split('}\n').forEach( json => {
			if (json.length == 0) return;
			let jsonObj: any = JSON.parse(json + '}');
			if (jsonObj?.type == 'resize') {
				let resize = <ResizeEvent> jsonObj;
				//console.log('w:' + resize.width + ', h:' + resize.height);
				win.setSize(resize.width, resize.height);
			} else if (jsonObj?.type == 'browse') {
				let browse = <BrowseEvent> jsonObj;
				if (browse.url == '') {
					win.loadFile(path.join(__dirname + '/index.html'));
				} else {
					win.loadURL(browse.url);
				}
			} else if (jsonObj?.type == 'quit') {
				win.close();
			} else {
				if (jsonObj?.type != 'mouseMove') {
					win.focusOnWebView();
				}
				if (jsonObj?.type == 'char') {
					let keyboardEvent = <KeyboardInputEvent> jsonObj;
					// workaround for 'Enter' as described on https://github.com/electron/electron/issues/8977
					if (keyboardEvent.keyCode == 'Return' || keyboardEvent.keyCode == 'Enter') {
						keyboardEvent.keyCode = String.fromCharCode(0x0D);
					}
				}
				win.webContents.sendInputEvent(<MouseInputEvent|MouseWheelInputEvent|KeyboardInputEvent> jsonObj);
			}
		});
	});
	//}).listen(9091, 'localhost');
});
