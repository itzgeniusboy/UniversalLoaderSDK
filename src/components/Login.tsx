import React from 'react';
import { useAuth } from '../lib/auth';
import { Button } from './ui/Button';
import { CheckSquare, ArrowRight } from 'lucide-react';
import { motion } from 'motion/react';

export const Login = () => {
  const { signIn } = useAuth();

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 px-4">
      <div className="w-full max-w-md space-y-8 text-center">
        <motion.div
           initial={{ scale: 0.9, opacity: 0 }}
           animate={{ scale: 1, opacity: 1 }}
           transition={{ duration: 0.5, ease: [0.19, 1, 0.22, 1] }}
           className="mx-auto flex h-20 w-20 items-center justify-center rounded-3xl bg-zinc-900 text-white shadow-2xl shadow-zinc-900/20"
        >
          <CheckSquare className="h-10 w-10" />
        </motion.div>

        <div className="space-y-2">
          <h1 className="text-4xl font-extrabold tracking-tight text-zinc-900">TaskOrbit</h1>
          <p className="text-zinc-500">Your minimal, powerful workspace for getting things done.</p>
        </div>

        <Card className="p-8 shadow-2xl shadow-zinc-200/50">
          <div className="space-y-6">
            <div className="space-y-2">
              <h2 className="text-xl font-bold">Welcome back</h2>
              <p className="text-sm text-zinc-500">Sign in to sync your tasks across all your devices.</p>
            </div>
            
            <Button 
                onClick={signIn} 
                className="w-full h-12 text-base font-semibold transition-all hover:translate-y-[-2px] active:translate-y-[0px] shadow-lg shadow-zinc-900/10"
            >
              Sign in with Google
              <ArrowRight className="ml-2 h-5 w-5" />
            </Button>
            
            <div className="flex items-center gap-4 py-2">
              <div className="h-px flex-1 bg-zinc-100"></div>
              <span className="text-xs font-medium uppercase tracking-widest text-zinc-300">Fast & Secure</span>
              <div className="h-px flex-1 bg-zinc-100"></div>
            </div>

            <p className="text-xs text-zinc-400">
              By signing in, you agree to our Terms of Service and Privacy Policy.
            </p>
          </div>
        </Card>

        <div className="grid grid-cols-2 gap-4">
            <div className="rounded-2xl border border-zinc-100 bg-white/50 p-4">
                <p className="text-xl font-bold">100%</p>
                <p className="text-[10px] uppercase tracking-wider text-zinc-400 font-bold">Secure</p>
            </div>
            <div className="rounded-2xl border border-zinc-100 bg-white/50 p-4">
                <p className="text-xl font-bold">Real-time</p>
                <p className="text-[10px] uppercase tracking-wider text-zinc-400 font-bold">Sync</p>
            </div>
        </div>
      </div>
    </div>
  );
};

// Internal Card for Login to avoid circular import issues if they arise
const Card = ({ children, className }: { children: React.ReactNode, className?: string }) => (
    <div className={`rounded-3xl border border-zinc-100 bg-white ${className}`}>
        {children}
    </div>
);
