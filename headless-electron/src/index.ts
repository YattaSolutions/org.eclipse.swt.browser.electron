import { app, BrowserWindow, KeyboardInputEvent, MouseInputEvent, MouseWheelInputEvent } from 'electron';
import net from 'net';
import path from 'path';
import { BrowseEvent, ResizeEvent } from './events';

let width = 800;
let height = 600;
let socket = 'ipcsockettest';
let args = process.argv;
if (args.length > 1) {
	args.shift();
	args.shift();
	while (args.length > 0) {
		const key = args.shift();
		const value = args.shift();
		if (!value) {
			console.log('Missing value for parameter: ' + key);
			process.exit(-1);
		}
		switch (key) {
			case '-width':
				width = +value;
				continue;
			case '-height':
				height = +value;
				continue;
			case '-socket':
				socket = value;
				continue;
			default:
				console.log('Unknown parameter: ' + key);
				process.exit(-1);
		}
	}
}

app.disableHardwareAcceleration();

//let client = net.connect(9090, 'localhost');
let client = net.connect('\\\\.\\pipe\\' + socket);
client.setEncoding('utf8');

app.on('ready', () => {
	console.log('Started Electron');

	const win = new BrowserWindow({
		width: width,
		height: height,
		show: false,
		frame: false,
		//transparent: true,
		webPreferences: { offscreen: true }
	});

	//win.webContents.beginFrameSubscription(true ,(image, dirtyRect) => {
	win.webContents.on('paint', (event, dirtyRect, image) => {
		const imageBytes: Buffer = image.crop(dirtyRect).toJPEG(100);
		writeCommand('paint:' + dirtyRect.x + ',' + dirtyRect.y + ',' + imageBytes.length);
		client.write(imageBytes);
	});

	win.webContents.on('cursor-changed', (event, type) => {
		writeCommand('cursor:' + type);
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

const writeCommand = (command: string): void => {
	client.write(command.padEnd(32, ','), 'utf-8');
}