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
  { id: 'admin-dash', name: 'dashboard.html', path: 'admin/dashboard.html', icon: <Settings size={18} />, lang: 'html' },
  { id: 'admin-js', name: 'admin.js', path: 'admin/admin.js', icon: <Terminal size={18} />, lang: 'javascript' },
  { id: 'verify-php', name: 'verify.php', path: 'server/verify.php', icon: <Cpu size={18} />, lang: 'php' },
  { id: 'customers-json', name: 'customers.json', path: 'admin/data/customers.json', icon: <Database size={18} />, lang: 'json' },
  { id: 'entry', name: 'OneCoreSDK.java', path: 'sdk/src/main/java/com/onecore/sdk/OneCoreSDK.java', icon: <Smartphone size={18} />, lang: 'java' },
  { id: 'license', name: 'SDKLicense.java', path: 'sdk/src/main/java/com/onecore/sdk/SDKLicense.java', icon: <ShieldCheck size={18} />, lang: 'java' },
  { id: 'container', name: 'VirtualContainer.java', path: 'sdk/src/main/java/com/onecore/sdk/VirtualContainer.java', icon: <Box size={18} />, lang: 'java' },
  { id: 'build', name: 'build.gradle', path: 'sdk/build.gradle', icon: <Settings size={18} />, lang: 'groovy' },
  { id: 'manifest', name: 'AndroidManifest.xml', path: 'sdk/src/main/AndroidManifest.xml', icon: <Package size={18} />, lang: 'xml' },
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
    <div className="flex h-screen bg-[var(--bg)] text-[var(--fg)] overflow-hidden font-sans">
      {/* Sidebar */}
      <aside className="w-64 bg-[var(--surface)] border-r border-[var(--line)] flex flex-col">
        <div className="p-6 border-b border-[var(--line)] bg-[var(--surface)]/50 backdrop-blur-md">
          <div className="text-[var(--accent)] font-bold text-[10px] tracking-widest uppercase mb-1 opacity-80">Project</div>
          <h1 className="text-xl font-bold text-white leading-tight tracking-[calc(-0.02em)]">
            OneCore <span className="font-light opacity-50">SDK</span>
          </h1>
          <div className="flex items-center mt-2">
            <div className="w-1.5 h-1.5 rounded-full bg-[var(--accent)] status-glow mr-2" />
            <span className="text-[10px] text-[var(--text-dim)] uppercase tracking-tight font-medium">Build Stable • 1.0.4</span>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto py-6">
          <div className="px-6 mb-4 text-[10px] font-bold text-[var(--text-dim)] uppercase tracking-[0.2em] opacity-60">
            Filesystem
          </div>
          <div className="space-y-0.5">
            {FILES.map((file) => (
              <button
                key={file.id}
                onClick={() => setSelectedFile(file)}
                className={`w-full flex items-center gap-3 px-6 py-2 text-sm transition-all relative group ${
                  selectedFile.id === file.id 
                  ? 'bg-white/5 text-white' 
                  : 'text-[var(--text-dim)] hover:text-white hover:bg-white/[0.02]'
                }`}
              >
                {selectedFile.id === file.id && (
                  <motion.div 
                    layoutId="sidebar-active"
                    className="absolute left-0 w-1 h-4 bg-[var(--accent)] rounded-r-full shadow-[0_0_8px_var(--accent-glow)]"
                  />
                )}
                <span className={`transition-transform duration-200 group-hover:scale-110 ${selectedFile.id === file.id ? 'text-[var(--accent)]' : 'opacity-40'}`}>
                  {file.icon}
                </span>
                <span className="flex-1 text-left truncate font-medium">{file.name}</span>
              </button>
            ))}
          </div>
        </nav>

        <div className="p-6 bg-black/20 border-t border-[var(--line)]">
          <div className="text-[10px] font-bold text-[var(--text-dim)] mb-4 uppercase tracking-widest opacity-50">SDK Metrics</div>
          <div className="space-y-2.5">
            <div className="flex justify-between text-[11px]">
              <span className="text-[var(--text-dim)]">Target API</span>
              <span className="text-[var(--accent)] font-bold">34</span>
            </div>
            <div className="flex justify-between text-[11px]">
              <span className="text-[var(--text-dim)]">Min API</span>
              <span className="text-[var(--accent)] font-bold">21</span>
            </div>
            <div className="flex justify-between text-[11px]">
              <span className="text-[var(--text-dim)]">Language</span>
              <span className="text-[var(--accent)] font-bold">Java</span>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Panel */}
      <main className="flex-1 flex flex-col overflow-hidden bg-[var(--bg)]">
        <header className="h-16 border-b border-[var(--line)] flex items-center justify-between px-8 ios-glass sticky top-0 z-10">
          <div className="flex items-center gap-8 h-full text-[13px] font-semibold">
            <span className="text-white relative h-full flex items-center">
              Source
              <motion.div layoutId="header-active" className="absolute bottom-0 left-0 right-0 h-0.5 bg-[var(--accent)] shadow-[0_-2px_8px_var(--accent-glow)]" />
            </span>
            <span className="text-[var(--text-dim)] h-full flex items-center hover:text-white transition-colors cursor-pointer">Tests</span>
            <span className="text-[var(--text-dim)] h-full flex items-center hover:text-white transition-colors cursor-pointer">Documentation</span>
          </div>

          <div className="flex items-center gap-3">
            <div className="px-3 py-1 bg-[var(--accent)]/10 text-[var(--accent)] text-[10px] font-bold rounded-full border border-[var(--accent)]/20 uppercase tracking-widest">
              AAR Ready
            </div>
            <div className="px-3 py-1 bg-white/5 text-[var(--text-dim)] text-[10px] font-bold rounded-full border border-white/10 uppercase tracking-widest">
              No NDK
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-auto p-10">
          <div className="max-w-6xl mx-auto space-y-10">
            {/* Quick Stats Grid */}
            <div className="grid grid-cols-4 gap-6">
              {[
                { label: 'Hook Engine', val: 'Native-Free', sub: 'Pure Java Hooking' },
                { label: 'Virtualization', val: 'Sandbox.v1', sub: 'Secure App Cloning' },
                { label: 'Spoofing', val: 'Bypass-X', sub: 'Hardware Masking' },
                { label: 'Memory', val: 'Direct/proc', sub: 'Runtime Memory Ops' }
              ].map((stat) => (
                <div key={stat.label} className="premium-card p-6 group hover:border-[var(--accent)]/30 transition-all duration-300">
                  <div className="text-[var(--accent)] text-[10px] font-bold uppercase mb-3 tracking-widest opacity-80">{stat.label}</div>
                  <div className="text-2xl font-bold text-white mb-1 tracking-tight">{stat.val}</div>
                  <div className="text-[11px] text-[var(--text-dim)] font-medium">{stat.sub}</div>
                </div>
              ))}
            </div>

            <div className="grid grid-cols-2 gap-10 min-h-[500px]">
              {/* Code Preview */}
              <div className="premium-card flex flex-col overflow-hidden">
                <div className="p-4 border-b border-[var(--line)] text-[10px] font-bold text-[var(--text-dim)] uppercase flex justify-between items-center bg-white/[0.02]">
                  <div className="flex items-center gap-2">
                    <div className="flex gap-1.5">
                      <div className="w-2.5 h-2.5 rounded-full bg-[#FF5F56]" />
                      <div className="w-2.5 h-2.5 rounded-full bg-[#FFBD2E]" />
                      <div className="w-2.5 h-2.5 rounded-full bg-[#27C93F]" />
                    </div>
                    <span className="ml-2 tracking-widest">{selectedFile.name}</span>
                  </div>
                  <div className="flex gap-2">
                    <button 
                      onClick={handleCopy}
                      className="bg-white/5 px-3 py-1 rounded-full text-[9px] hover:bg-white/10 transition-all border border-white/5 uppercase flex items-center gap-1.5 active:scale-95"
                    >
                      {copied ? <Check size={10} className="text-[var(--accent)]" /> : <Copy size={10} />}
                      {copied ? 'COPIED' : 'COPY'}
                    </button>
                  </div>
                </div>
                <div className="flex-1 p-8 font-mono text-[12px] leading-relaxed text-gray-300 overflow-auto bg-black/40">
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={selectedFile.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -10 }}
                      transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
                    >
                      <div className="text-blue-400">package</div> com.onecore.sdk;<br /><br />
                      <div className="text-blue-400">public class</div> {selectedFile.name.replace('.java', '')} {'{'}<br />
                      &nbsp;&nbsp;<div className="text-zinc-500 italic">// Source implementation for {selectedFile.name}</div><br />
                      &nbsp;&nbsp;<div className="text-zinc-500 italic">// {selectedFile.path}</div><br /><br />
                      &nbsp;&nbsp;<div className="text-blue-400">public static void</div> init() {'{'}<br />
                      &nbsp;&nbsp;&nbsp;&nbsp;Logger.i(<div className="text-[var(--accent)]">"Component {selectedFile.id} initialized"</div>);<br />
                      &nbsp;&nbsp;{'}'}<br />
                      {'}'}
                    </motion.div>
                  </AnimatePresence>
                </div>
              </div>

              {/* Build Config & Console */}
              <div className="premium-card p-8 flex flex-col justify-between shadow-2xl overflow-hidden relative group">
                <div className="absolute top-0 right-0 w-32 h-32 bg-[var(--accent)]/5 blur-[80px] rounded-full -mr-16 -mt-16 group-hover:bg-[var(--accent)]/10 transition-colors duration-500" />
                
                <div className="space-y-8 overflow-y-auto pr-2 relative z-10">
                  <div>
                    <div className="text-[var(--accent)] text-[10px] font-bold uppercase mb-6 tracking-[0.2em] opacity-80">Build Configuration</div>
                    <div className="space-y-4">
                      {[
                        'OneCore Engine - Advanced Hooking',
                        'Android API Level 26 - 34 Support',
                        'Experimental Virtual Layer v4.2'
                      ].map((item) => (
                        <div key={item} className="flex items-center">
                          <div className="w-1 h-1 bg-[var(--accent)] rounded-full mr-4 shadow-[0_0_8px_var(--accent)]" />
                          <div className="text-[13px] text-gray-300 font-medium">{item}</div>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div>
                    <div className="text-[var(--accent)] text-[10px] font-bold uppercase mb-4 tracking-[0.2em] opacity-80">Process Log</div>
                    <div className="bg-black/60 backdrop-blur-sm p-5 rounded-xl text-[10px] font-mono text-[var(--accent)] border border-white/5 leading-relaxed shadow-inner">
                      <div className="opacity-50 tracking-tighter">[{new Date().toLocaleTimeString()}] SDK Init...</div>
                      <div className="mt-1">{"> Task :sdk:bundleReleaseAar"}</div>
                      <div className="text-white mt-1">BUILD SUCCESSFUL</div>
                      <div className="opacity-50 mt-1">25 actionable tasks • 12.4s</div>
                    </div>
                  </div>
                </div>

                <button className="w-full py-4 bg-[var(--accent)] text-black font-black text-[12px] uppercase tracking-[0.25em] rounded-full hover:brightness-110 active:scale-[0.98] transition-all mt-8 accent-glow">
                  Export SDK Package
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
