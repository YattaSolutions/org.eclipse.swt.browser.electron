package de.yatta.browser.electron;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.scalasbt.ipcsocket.Win32NamedPipeServerSocket;
import org.scalasbt.ipcsocket.Win32SecurityLevel;

class ElectronBrowserCanvas extends Canvas
{
   private Image image;
   private GC gc;

   public ElectronBrowserCanvas(Composite parent)
   {
      super(parent, SWT.NO_REDRAW_RESIZE | SWT.DOUBLE_BUFFERED);

      Display display = getDisplay();
      image = new Image(display, display.getBounds().width, display.getBounds().height);
      gc = new GC(image);

      addControlListener(new ControlListener() {
         @Override
         public void controlResized(ControlEvent e)
         {
            Rectangle oldBounds = image.getBounds();
            Rectangle newBounds = getBounds();
            if (oldBounds.width < newBounds.width || oldBounds.height < newBounds.height)
            {
               image.dispose();
               image = new Image(e.display, newBounds.width, newBounds.height);
               gc = new GC(image);
            }
            sendResize(newBounds);
         }

         @Override
         public void controlMoved(ControlEvent e)
         {
         }
      });

      addPaintListener(new PaintListener() {
         @Override
         public void paintControl(PaintEvent event)
         {
            event.gc.drawImage(image, event.x, event.y, event.width, event.height, event.x, event.y, event.width, event.height);
         }
      });

      addMouseListener(new MouseListener() {
         @Override
         public void mouseUp(MouseEvent e)
         {
            handleEvent("mouseUp", e);

         }

         @Override
         public void mouseDown(MouseEvent e)
         {
            handleEvent("mouseDown", e);
         }

         @Override
         public void mouseDoubleClick(MouseEvent e)
         {
         }
      });

      addMouseMoveListener(new MouseMoveListener() {
         @Override
         public void mouseMove(MouseEvent e)
         {
            handleEvent("mouseMove", e);
         }
      });

      addKeyListener(new KeyListener() {
         @Override
         public void keyReleased(KeyEvent e)
         {
            handleEvent("keyUp", e);
         }

         @Override
         public void keyPressed(KeyEvent e)
         {
            handleEvent("keyDown", e);
            handleEvent("char", e);
         }
      });
      addMouseWheelListener(new MouseWheelListener() {

         @Override
         public void mouseScrolled(MouseEvent e)
         {
            if (e.count != 0)
            {
               handleEvent("mouseWheel", e); // TODO how to correctly calculate deltaY etc.
            }
         }
      });
      addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            sendMessage("{\n" +
                  "   \"type\":\"quit\"\n" + "}");
            if (process != null) process.destroy();
         }
      });

      new Thread(() -> {
         listen(9090);
      }).start();

      //unzipAndStartElectron();
   }

   private ServerSocket server;

   private void listen(int port)
   {
      try
      {
         server = new Win32NamedPipeServerSocket("\\\\.\\pipe\\ipcsockettest", false, Win32SecurityLevel.LOGON_DACL);
         //server = new ServerSocket(port);
      }
      catch (IOException e)
      {
         System.err.println("Could not open port " + port);
         System.exit(-1);
      }

      new Thread(() -> {
         while (!isDisposed())
         {
            run(port);
            try
            {
               Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
               e.printStackTrace();
            }
         }
      }).start();
   }

   private PrintWriter out;

   private void run(int port)
   {
      try
      {
         System.out.println("Open connection");
         final Socket socket = server.accept();
         final InputStream inputStream = socket.getInputStream();
         final InputStreamReader streamReader = new InputStreamReader(inputStream);
         BufferedReader br = new BufferedReader(streamReader);

         out = new PrintWriter(socket.getOutputStream());

         Display display = getDisplay();
         display.asyncExec(() -> sendResize(getBounds()));

         Decoder decoder = Base64.getDecoder();
         String line = null;
         while (!isDisposed() && (line = br.readLine()) != null)
         {
            /*if ("getResize".equals(line))
            {
               display.asyncExec(() -> sendResize(getBounds()));
               continue;
            }*/
            int index = line.indexOf(':');
            if (index > 0)
            {
               String[] split = line.substring(0, index).split(",");
               Image image = new Image(display, new ByteArrayInputStream(decoder.decode(line.substring(index + 1))));
               Point point = new Point(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
               Rectangle bounds = image.getBounds();
               display.asyncExec(() -> { // TODO sync or async?
                  gc.drawImage(image, point.x, point.y);
                  redraw(point.x, point.y, bounds.width, bounds.height, false);
                  image.dispose();
               });
            }
         }
      }
      catch (IOException e)
      {
         out = null;
         System.err.println("Error: " + port);
         e.printStackTrace();
      }
   }

   private void sendMessage(String type, Map<String, String> parameters)
   {
      StringBuilder builder = new StringBuilder();
      parameters.forEach((key, value) -> builder.append(",\n" + "   \"" + key + "\":" + value));
      sendMessage("{\n" + "   \"type\":\"" + type + "\"" + builder.toString() + "\n" + "}");
   }

   private void sendMessage(String message)
   {
      if (out != null)
      {
         out.write(message + "\n");
         out.flush();
      }
      /*try
      {
         Socket sock = new Socket("localhost", 9091);
         PrintWriter out = new PrintWriter(sock.getOutputStream());
         out.write(message + "\n");
         out.flush();
         sock.close();
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }*/
   }
   
   public void browse(String url)
   {
      sendMessage("browse", Collections.singletonMap("url", "\"" + url + "\""));
   }

   private void sendResize(Rectangle bounds)
   {
      Map<String, String> params = new LinkedHashMap<>();
      params.put("width", Integer.toString(bounds.width));
      params.put("height", Integer.toString(bounds.height));
      sendMessage("resize", params);
   }

   private void handleEvent(String type, MouseEvent e)
   {
      String button;
      switch (e.button)
      {
         case 1:
            button = "left";
            break;
         case 2:
            button = "middle";
            break;
         case 3:
            button = "right";
            break;
         default:
            button = null;
            break;
      }
      
      Map<String, String> params = new LinkedHashMap<>();
      params.put("x", Integer.toString(e.x));
      params.put("y", Integer.toString(e.y));
      if (button != null) params.put("button", "\"" + button + "\"");
      if (!"mouseWheel".equals(type))
      {
         params.put("clickCount", Integer.toString(e.count));
      }
      else
      {
         params.put("deltaY", Integer.toString(e.count * 10));
      }
      sendMessage(type, params);
   }

   private void handleEvent(String type, KeyEvent e)
   {
      System.out.println(e.keyCode);
      String keyCode = null;
      if (Character.isLetterOrDigit(e.character) || '@' == e.character || '.' == e.character)
      {
         keyCode = Character.toString(e.character);
      }
      else
      {
         char c = '\0';
         switch (e.keyCode)
         {
            case 8:
               keyCode = "Backspace";
               break;
            case 9:
               keyCode = "Tab";
               break;
            case 13:
               keyCode = "Return";
               break;
            case 27:
               keyCode = "Escape";
               break;
            case 32:
               keyCode = "Space";
               c = ' ';
               break;
            case SWT.ARROW_UP:
               keyCode = "Up";
               break;
            case SWT.ARROW_DOWN:
               keyCode = "Down";
               break;
            case SWT.ARROW_LEFT:
               keyCode = "Left";
               break;
            case SWT.ARROW_RIGHT:
               keyCode = "Right";
               break;
            case SWT.PAGE_UP:
               keyCode = "PageUp";
               break;
            case SWT.PAGE_DOWN:
               keyCode = "PageDown";
               break;
            case SWT.HOME:
               keyCode = "Home";
               break;
            case SWT.END:
               keyCode = "End";
               break;
            case SWT.KEYPAD_ADD:
               keyCode = "Plus";
               c = '+';
               break;
            case SWT.KEYPAD_CR:
               keyCode = "Enter";
               break;
            case SWT.F1:
            case SWT.F2:
            case SWT.F3:
            case SWT.F4:
            case SWT.F5:
            case SWT.F6:
            case SWT.F7:
            case SWT.F8:
            case SWT.F9:
            case SWT.F10:
            case SWT.F11:
            case SWT.F12:
               keyCode = "F" + (e.keyCode - SWT.F1 + 1);
               break;
            default:
               return;
         }
         if ("char".equals(type))
         {
            if (c == '\0')
               return;
            keyCode = Character.toString(c);
         }
      }

      sendMessage(type, Collections.singletonMap("keyCode", "\"" + keyCode + "\""));
   }

   private Process process;
   
   private void unzipAndStartElectron()
   {
      File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "headless-electron"); // TODO unzip to AppData (see Launcher)
      if(!tempDir.exists())
      {
         tempDir.mkdirs();
      }
      try
      {
         unzip(getClass().getResourceAsStream("/headless-electron-win32-x64-0.0.1.zip"), tempDir);
         ProcessBuilder processBuilder = new ProcessBuilder(tempDir + File.separator + "headless-electron.exe");
         process = processBuilder.start();
      }
      catch (IOException e1)
      {
         e1.printStackTrace();
      }
   }

   private void unzip(InputStream is, File dest) throws IOException
   {
      try (ZipInputStream zip = new ZipInputStream(is))
      {
         ZipEntry entry = zip.getNextEntry();
         while (entry != null)
         {
            File out = new File(dest, entry.getName());
            File parentFile = out.getParentFile();
            if (!parentFile.exists())
            {
               parentFile.mkdirs();
            }
            if (!entry.isDirectory())
            {
               byte[] buffer = new byte[4096];
               try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out)))
               {
                  int count = 0;
                  while ((count = zip.read(buffer)) != -1)
                  {
                     bos.write(buffer, 0, count);
                  }
               }
            }
            else
            {
               out.mkdirs();
            }
            zip.closeEntry();
            entry = zip.getNextEntry();
         }
      }
   }
}