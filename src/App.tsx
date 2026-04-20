import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Code2, 
  Box, 
  Cpu, 
  Smartphone, 
  Database, 
  AlertTriangle, 
  Terminal, 
  ChevronRight, 
  Copy, 
  Check,
  Package,
  Layers,
  Settings,
  ShieldCheck
} from 'lucide-react';

const FILES = [
  { id: 'loader', name: 'LoaderSDK.java', path: 'app/src/main/java/com/loader/sdk/LoaderSDK.java', icon: <Smartphone size={18} />, lang: 'java' },
  { id: 'container', name: 'VirtualContainer.java', path: 'app/src/main/java/com/loader/sdk/VirtualContainer.java', icon: <Box size={18} />, lang: 'java' },
  { id: 'hook', name: 'HookEngine.java', path: 'app/src/main/java/com/loader/sdk/HookEngine.java', icon: <Cpu size={18} />, lang: 'java' },
  { id: 'spoofer', name: 'DeviceSpoofer.java', path: 'app/src/main/java/com/loader/sdk/DeviceSpoofer.java', icon: <ShieldCheck size={18} />, lang: 'java' },
  { id: 'memory', name: 'MemoryReader.java', path: 'app/src/main/java/com/loader/sdk/MemoryReader.java', icon: <Database size={18} />, lang: 'java' },
  { id: 'anti', name: 'AntiDetect.java', path: 'app/src/main/java/com/loader/sdk/AntiDetect.java', icon: <ShieldCheck size={18} />, lang: 'java' },
  { id: 'license', name: 'LicenseManager.java', path: 'app/src/main/java/com/loader/sdk/LicenseManager.java', icon: <Check size={18} />, lang: 'java' },
  { id: 'updater', name: 'Updater.java', path: 'app/src/main/java/com/loader/sdk/Updater.java', icon: <ChevronRight size={18} />, lang: 'java' },
  { id: 'config', name: 'RemoteConfig.java', path: 'app/src/main/java/com/loader/sdk/RemoteConfig.java', icon: <Layers size={18} />, lang: 'java' },
  { id: 'analytics', name: 'Analytics.java', path: 'app/src/main/java/com/loader/sdk/Analytics.java', icon: <Terminal size={18} />, lang: 'java' },
  { id: 'menu', name: 'FloatingMenu.java', path: 'app/src/main/java/com/loader/sdk/FloatingMenu.java', icon: <Layers size={18} />, lang: 'java' },
  { id: 'crash', name: 'CrashHandler.java', path: 'app/src/main/java/com/loader/sdk/utils/CrashHandler.java', icon: <AlertTriangle size={18} />, lang: 'java' },
  { id: 'logger', name: 'Logger.java', path: 'app/src/main/java/com/loader/sdk/utils/Logger.java', icon: <Terminal size={18} />, lang: 'java' },
  { id: 'native-hook-java', name: 'NativeHook.java', path: 'app/src/main/java/com/loader/sdk/NativeHook.java', icon: <Cpu size={18} />, lang: 'java' },
  { id: 'native-hook-cpp', name: 'native_hook.cpp', path: 'app/src/main/cpp/native_hook.cpp', icon: <Cpu size={18} />, lang: 'cpp' },
  { id: 'native-mem-cpp', name: 'memory.cpp', path: 'app/src/main/cpp/memory.cpp', icon: <Database size={18} />, lang: 'cpp' },
  { id: 'cmake', name: 'CMakeLists.txt', path: 'app/src/main/cpp/CMakeLists.txt', icon: <Settings size={18} />, lang: 'cmake' },
  { id: 'ci', name: 'build.yml', path: '.github/workflows/build.yml', icon: <Settings size={18} />, lang: 'yaml' },
  { id: 'readme', name: 'README.md', path: '/README.md', icon: <Package size={18} />, lang: 'markdown' },
  { id: 'deploy', name: 'DEPLOY.md', path: '/DEPLOY.md', icon: <ChevronRight size={18} />, lang: 'markdown' },
  { id: 'checklist', name: 'CHECKLIST.md', path: '/CHECKLIST.md', icon: <ShieldCheck size={18} />, lang: 'markdown' },
  { id: 'build', name: 'build.gradle', path: 'app/build.gradle', icon: <Settings size={18} />, lang: 'groovy' },
  { id: 'manifest', name: 'AndroidManifest.xml', path: 'app/src/main/AndroidManifest.xml', icon: <Package size={18} />, lang: 'xml' },
];

export default function App() {
  const [selectedFile, setSelectedFile] = useState(FILES[0]);
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    // In a real app we'd fetch the actual file content, 
    // but here we just simulate it to keep the UI responsive.
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex h-screen bg-[#050505] text-[#E0E0E0] overflow-hidden font-sans">
      {/* Sidebar */}
      <aside className="w-64 bg-[#0A0A0A] border-r border-[#1F1F1F] flex flex-col">
        <div className="p-6 border-b border-[#1F1F1F]">
          <div className="text-[#F27D26] font-bold text-xs tracking-widest uppercase mb-1">Project</div>
          <h1 className="text-lg font-semibold text-white leading-tight">
            UniversalLoaderSDK
          </h1>
          <div className="flex items-center mt-2">
            <div className="w-2 h-2 rounded-full bg-green-500 mr-2 status-glow" />
            <span className="text-[10px] text-gray-400 uppercase tracking-tighter font-mono">Build Stable • v1.0.4</span>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto py-4">
          <div className="px-6 mb-4 text-[11px] font-bold text-gray-500 uppercase tracking-widest">
            FILESYSTEM
          </div>
          {FILES.map((file) => (
            <button
              key={file.id}
              onClick={() => setSelectedFile(file)}
              className={`w-full flex items-center gap-2 px-6 py-1.5 text-sm transition-all relative group font-mono ${
                selectedFile.id === file.id 
                ? 'bg-[#1A1A1A] text-white border-l-2 border-[#F27D26]' 
                : 'hover:bg-[#1A1A1A]/80 text-gray-400 hover:text-gray-200'
              }`}
            >
              <span className={`mr-1 opacity-50 ${selectedFile.id === file.id ? 'text-[#F27D26] opacity-100' : ''}`}>
                📄
              </span>
              <span className="flex-1 text-left truncate">{file.name}</span>
            </button>
          ))}
        </nav>

        <div className="p-6 bg-[#080808] border-t border-[#1F1F1F]">
          <div className="text-[10px] font-bold text-gray-600 mb-3 uppercase tracking-widest">SDK Metrics</div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-[11px] font-mono">
              <span className="text-gray-500">Target API</span>
              <span className="text-[#F27D26]">34</span>
            </div>
            <div className="flex justify-between text-[11px] font-mono">
              <span className="text-gray-500">Min API</span>
              <span className="text-[#F27D26]">26</span>
            </div>
            <div className="flex justify-between text-[11px] font-mono">
              <span className="text-gray-500">Language</span>
              <span className="text-[#F27D26]">Java</span>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Panel */}
      <main className="flex-1 flex flex-col overflow-hidden">
        <header className="h-16 border-b border-[#1F1F1F] flex items-center justify-between px-8 bg-[#050505]">
          <div className="flex items-center gap-6 h-full text-sm font-medium">
            <span className="text-white border-b-2 border-[#F27D26] h-full flex items-center mt-0.5">Source</span>
            <span className="text-gray-500 h-full flex items-center hover:text-gray-300 cursor-pointer">Tests</span>
            <span className="text-gray-500 h-full flex items-center hover:text-gray-300 cursor-pointer">Documentation</span>
          </div>

          <div className="flex items-center gap-2">
            <div className="px-3 py-1 bg-green-900/20 text-green-400 text-[10px] font-bold rounded-full border border-green-800/30 uppercase tracking-widest">
              AAR Ready
            </div>
            <div className="px-3 py-1 bg-white/5 text-gray-400 text-[10px] font-bold rounded-full border border-white/10 uppercase tracking-widest">
              No NDK
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-auto p-8 bg-[#070707]">
          <div className="max-w-6xl mx-auto space-y-8">
            {/* Quick Stats Grid */}
            <div className="grid grid-cols-4 gap-4">
              <div className="bg-[#0A0A0A] border border-[#1F1F1F] p-5 rounded-lg">
                <div className="text-[#F27D26] text-xs font-bold uppercase mb-2">Hook Engine</div>
                <div className="text-2xl font-light text-white mb-1">Native-Free</div>
                <div className="text-[10px] text-gray-500">Pure Java Hooking</div>
              </div>
              <div className="bg-[#0A0A0A] border border-[#1F1F1F] p-5 rounded-lg">
                <div className="text-[#F27D26] text-xs font-bold uppercase mb-2">Virtualization</div>
                <div className="text-2xl font-light text-white mb-1">Sandbox.v1</div>
                <div className="text-[10px] text-gray-500">Secure App Cloning</div>
              </div>
              <div className="bg-[#0A0A0A] border border-[#1F1F1F] p-5 rounded-lg">
                <div className="text-[#F27D26] text-xs font-bold uppercase mb-2">Spoofing</div>
                <div className="text-2xl font-light text-white mb-1">Bypass-X</div>
                <div className="text-[10px] text-gray-500">Hardware Masking</div>
              </div>
              <div className="bg-[#0A0A0A] border border-[#1F1F1F] p-5 rounded-lg">
                <div className="text-[#F27D26] text-xs font-bold uppercase mb-2">Memory</div>
                <div className="text-2xl font-light text-white mb-1">Direct/proc</div>
                <div className="text-[10px] text-gray-500">Runtime Memory Ops</div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-8 h-[500px]">
              {/* Code Preview */}
              <div className="bg-[#0A0A0A] border border-[#1F1F1F] rounded-lg flex flex-col overflow-hidden">
                <div className="p-4 border-b border-[#1F1F1F] text-xs font-mono text-gray-500 uppercase flex justify-between items-center bg-[#0C0C0C]">
                  <span>Preview: {selectedFile.name}</span>
                  <div className="flex gap-2">
                    <button 
                      onClick={handleCopy}
                      className="bg-white/5 px-2 py-0.5 rounded text-[10px] hover:bg-white/10 transition-colors uppercase flex items-center gap-1"
                    >
                      {copied ? <Check size={10} /> : <Copy size={10} />}
                      {copied ? 'COPIED' : 'COPY'}
                    </button>
                    <span className="bg-white/5 px-2 py-0.5 rounded text-[10px]">READ ONLY</span>
                  </div>
                </div>
                <div className="flex-1 p-6 font-mono text-[11px] leading-relaxed text-gray-400 overflow-auto bg-[#050505]">
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={selectedFile.id}
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.2 }}
                    >
                      <div className="text-blue-400">package</div> com.loader.sdk;<br /><br />
                      <div className="text-blue-400">public class</div> {selectedFile.name.replace('.java', '')} {'{'}<br />
                      &nbsp;&nbsp;<div className="text-gray-500">// Source implementation for {selectedFile.name}</div><br />
                      &nbsp;&nbsp;<div className="text-gray-500">// {selectedFile.path}</div><br /><br />
                      &nbsp;&nbsp;<div className="text-blue-400">public static void</div> init() {'{'}<br />
                      &nbsp;&nbsp;&nbsp;&nbsp;Logger.i(<div className="text-green-500">"Component {selectedFile.id} initialized"</div>);<br />
                      &nbsp;&nbsp;{'}'}<br />
                      {'}'}
                    </motion.div>
                  </AnimatePresence>
                </div>
              </div>

              {/* Build Config & Console */}
              <div className="bg-[#0A0A0A] border border-[#1F1F1F] rounded-lg p-6 flex flex-col justify-between shadow-2xl overflow-hidden">
                <div className="space-y-8 overflow-y-auto pr-2">
                  <div>
                    <div className="text-[#F27D26] text-xs font-bold uppercase mb-4 tracking-widest">Build Configuration</div>
                    <div className="space-y-3">
                      <div className="flex items-center">
                        <div className="w-1.5 h-1.5 bg-[#F27D26] rounded-full mr-3" />
                        <div className="text-sm text-gray-300">Pure Java Implementation (No NDK)</div>
                      </div>
                      <div className="flex items-center">
                        <div className="w-1.5 h-1.5 bg-[#F27D26] rounded-full mr-3" />
                        <div className="text-sm text-gray-300">AndroidIDE Compatibility Layer</div>
                      </div>
                      <div className="flex items-center">
                        <div className="w-1.5 h-1.5 bg-[#F27D26] rounded-full mr-3" />
                        <div className="text-sm text-gray-300">API Level 26 - 34 Global Support</div>
                      </div>
                    </div>
                  </div>

                  <div>
                    <div className="text-[#F27D26] text-xs font-bold uppercase mb-3 tracking-widest">Compilation Log</div>
                    <div className="bg-black p-4 rounded text-[10px] font-mono text-green-400 border border-[#1F1F1F] leading-relaxed">
                      {"> Task :app:bundleReleaseAar UP-TO-DATE"}<br />
                      {"> Task :app:assembleRelease"}<br />
                      BUILD SUCCESSFUL in 12s<br />
                      25 actionable tasks: 2 up-to-date
                    </div>
                  </div>
                </div>

                <button className="w-full py-4 bg-[#F27D26] text-black font-bold text-[11px] uppercase tracking-[0.2em] hover:bg-[#F49147] transition-all transform hover:scale-[1.01] active:scale-[0.99] mt-6 shadow-lg shadow-orange-950/20">
                  Download Compiled AAR
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
