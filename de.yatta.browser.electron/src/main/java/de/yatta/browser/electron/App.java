package de.yatta.browser.electron;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class App
{

   public static void main(String[] args)
   {
      boolean startElectron = true;
      for (int i = 0; i < args.length; i++)
      {
         switch (args[i])
         {
            case "-skipStart":
               startElectron = false;
               continue;
            default:
               System.err.println("Unknown parameter: " + args[i]);
               System.exit(-1);;
         }
      }
      new App().start(startElectron);
   }

   public void start(boolean startElectron)
   {
      Display display = new Display();

      Shell shell = new Shell(display);
      shell.setText("Electron in SWT");
      //shell.setLayout(new FillLayout());

      GridLayout layout = new GridLayout(1, false);
      layout.marginHeight = 0;
      layout.marginWidth = 0;
      shell.setLayout(layout);


      String url = "https://google.com";
      Text addressBar = new Text(shell, SWT.SINGLE);
      addressBar.setText(url);
      GridData layoutData1 = new GridData(SWT.FILL, SWT.CENTER, true, false);
      addressBar.setLayoutData(layoutData1);

      ElectronBrowserCanvas canvas = new ElectronBrowserCanvas(shell, startElectron);
      GridData layoutData2 = new GridData(SWT.FILL, SWT.FILL, true, true);
      canvas.setLayoutData(layoutData2);

      addressBar.addListener(SWT.Traverse, event -> {
         if (event.detail == SWT.TRAVERSE_RETURN)
         {
            String text = addressBar.getText();
            if (!text.startsWith("http"))
            {
               text = "http://" + text;
               addressBar.setText(text);
            }
            canvas.browse(text);
         }
      });


      shell.open();
      while (!shell.isDisposed())
      {
         if (!display.readAndDispatch())
         {
            display.sleep();
         }
      }
      display.dispose();
   }
}
