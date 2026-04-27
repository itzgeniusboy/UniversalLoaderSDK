import React, { useState, useEffect } from 'react';
import { useAuth } from '../lib/auth';
import { getUserProfile, updateUserProfile, UserProfile } from '../services/userService';
import { Card, CardContent, CardHeader, CardTitle } from './ui/Card';
import { Button } from './ui/Button';
import { Input } from './ui/Input';
import { Bell, Moon, Sun, Smartphone, User, Save, CheckCircle2 } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

export const Settings = () => {
  const { user } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (user) {
      getUserProfile(user.uid).then(setProfile);
    }
  }, [user]);

  const handleUpdate = async () => {
    if (!user || !profile) return;
    setSaving(true);
    await updateUserProfile(user.uid, {
      displayName: profile.displayName,
      theme: profile.theme,
      notificationsEnabled: profile.notificationsEnabled,
    });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  if (!profile) return <div>Loading...</div>;

  return (
    <div className="max-w-2xl space-y-8">
      <div className="space-y-1">
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-zinc-500">Manage your account settings and preferences.</p>
      </div>

      <div className="grid gap-8">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <User className="h-5 w-5 text-zinc-400" />
              Profile Information
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-700">Display Name</label>
              <Input 
                value={profile.displayName} 
                onChange={e => setProfile({...profile, displayName: e.target.value})}
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-700">Email Address</label>
              <Input value={profile.email} disabled className="bg-zinc-50 text-zinc-500" />
              <p className="text-xs text-zinc-400 italic">Email cannot be changed from settings.</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Moon className="h-5 w-5 text-zinc-400" />
              Appearance & Theme
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-3 gap-3">
              {(['light', 'dark', 'system'] as const).map((t) => {
                const Icon = t === 'light' ? Sun : t === 'dark' ? Moon : Smartphone;
                const isActive = profile.theme === t;
                return (
                  <button
                    key={t}
                    onClick={() => setProfile({...profile, theme: t})}
                    className={`flex flex-col items-center justify-center gap-2 rounded-xl border-2 p-4 transition-all ${
                      isActive 
                        ? 'border-zinc-900 bg-zinc-50 text-zinc-900 shadow-sm' 
                        : 'border-zinc-100 bg-white text-zinc-500 hover:border-zinc-200 hover:text-zinc-700'
                    }`}
                  >
                    <Icon className="h-6 w-6" />
                    <span className="text-xs font-semibold uppercase">{t}</span>
                  </button>
                );
              })}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Bell className="h-5 w-5 text-zinc-400" />
              Notifications
            </CardTitle>
          </CardHeader>
          <CardContent className="flex items-center justify-between">
            <div className="space-y-0.5">
              <p className="text-sm font-medium">Desktop Notifications</p>
              <p className="text-xs text-zinc-500">Receive alerts when tasks reach their due date.</p>
            </div>
            <button
              onClick={() => setProfile({...profile, notificationsEnabled: !profile.notificationsEnabled})}
              className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-zinc-900 focus:ring-offset-2 ${
                profile.notificationsEnabled ? 'bg-zinc-900' : 'bg-zinc-200'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                  profile.notificationsEnabled ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </CardContent>
        </Card>

        <div className="flex items-center justify-end gap-4 border-t border-zinc-200 pt-8">
          <AnimatePresence>
            {saved && (
              <motion.div 
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0 }}
                className="flex items-center gap-1.5 text-sm font-medium text-green-600"
              >
                <CheckCircle2 className="h-4 w-4" />
                Settings saved
              </motion.div>
            )}
          </AnimatePresence>
          <Button 
            onClick={handleUpdate} 
            disabled={saving}
            className="w-32"
          >
            {saving ? 'Saving...' : (
              <>
                <Save className="mr-2 h-4 w-4" />
                Save Changes
              </>
            )}
          </Button>
        </div>
      </div>
    </div>
  );
};
