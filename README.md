[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](LICENSE)

# Electron for SWT
** Chromium browser integration for SWT based on Electron
This prototype provides an integration of Electron in SWT. It is the first step on the journey to a modern browser for the Eclipse IDE.

# Modern browser in Eclipse

### Challenge
Open ecosystems like Eclipse play a major role in providing developers an independent and open environment to build software. Although the Eclipse IDE is an great and flexible solution for software development, one thing has bothered us and others: The current version of Eclipse uses operating system dependent, outdated browser technologies. This issue was already addresses and discussed by the community at [Bugzilla](https://bugs.eclipse.org/bugs/show_bug.cgi?id=405031).

### Approach
We use Electron (with a small node.js script) to render a given website. We are using the electron [offscreen rendering mode](https://www.electronjs.org/docs/latest/tutorial/offscreen-rendering), here. The resulting image is then transfered via IPC to an SWT process where it is finally displayed. Dirty regions are supported to speed-up the transfer.

Interaction (like keystrokes) are send back to the Electron process. Such hard decoupling eliminates common issues caused by running Chromium and SWT in the same process.

Currently the communication between Electron and SWT process is done via socket communication. Maybe that could be changed to shared memory in future.

### Opportunities
Overall, providing modern web technology has leverage on many other Eclipse projects like the [Eclipse Marketplace](https://projects.eclipse.org/projects/technology.packaging.mpc).

The current approach is rather simple, and therfore easy to maintain and integrates easily in SWT.

Moreover, having an embedded Chrome browser of the same version for every operating system also enables developers to develop and test with little overhead. We will benefit from frequent updates of Chromium (via Electron), so we ensure future-proof technology with every new release cycle.

### Possible drawbacks
Using software rendering has potential drawbacks
- Reduces possible framerate
- WebGL and 3D CSS animations are not supported

GPU acceleration would be supported by the Electron offscreen rendering if really needed, but make things even slower, since then content is rendered on GPU, copied to memory transfered to the SWT process and the displayed. That reduces the framerate even more.

But in our opinion it should work good for most Eclipse plugins like e.g., MPC.

Also using Electron not only introduces a dependency to Chromium but also to node.js. That would require even more security updates. We believe that those could be easily done automatically.

### Known issues
[Issues with SWT and retina displays for Mac](https://bugs.eclipse.org/bugs/show_bug.cgi?id=576761)

## Tech stack and credits
This project is based and dependent on the following major solutions and technologies:
- [Chromium](http://www.chromium.org/Home)
- [Electron](https://www.electronjs.org)
- [SWT](https://wiki.eclipse.org/SWT)

## Getting started

### How to build and run from source

```bash
# clone the github repository
git clone https://github.com/YattaSolutions/org.eclipse.swt.browser.electron.git 
cd org.eclipse.swt.browser.electron

# build packed electron executable
cd headless-electron
npm install
npm run compile
npm run package
npm run make
cd ..

# copy packed electron executable to SWT project (for win64 - adapt for other platforms)
cp headless-electron/out/make/zip/win32/x64/headless-electron-win32-x64-0.0.1.zip de.yatta.browser.electron/src/main/resources/

# build and package the prototype
cd de.yatta.browser.electron
mvn package

# run prototype
java -jar target/browser.electron-0.0.6-SNAPSHOT-jar-with-dependencies.jar
```

## License
*Electron for SWT* is open sourced under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).

## Contributing
Every contributions brings us closer to a better Eclipse IDE exprience. So, please feel free to engage using issues, pull request or get in touch with us directly under opensource@yatta.de.
