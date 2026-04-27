import React, { useState, useEffect } from 'react';
import { useAuth } from '../lib/auth';
import { subscribeToTasks, Task } from '../services/taskService';
import { Card, CardContent, CardHeader, CardTitle } from './ui/Card';
import { Button } from './ui/Button';
import { CheckSquare, Clock, AlertCircle, Calendar, ArrowRight, Activity } from 'lucide-react';
import { Link } from 'react-router-dom';
import { motion } from 'motion/react';

export const Dashboard = () => {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);

  useEffect(() => {
    if (user) {
      const unsubscribe = subscribeToTasks(user.uid, (data) => {
        setTasks(data);
      });
      return unsubscribe;
    }
  }, [user]);

  const stats = {
    total: tasks.length,
    completed: tasks.filter(t => t.completed).length,
    pending: tasks.filter(t => !t.completed).length,
    completionRate: tasks.length > 0 ? Math.round((tasks.filter(t => t.completed).length / tasks.length) * 100) : 0,
  };

  const recentTasks = tasks.slice(0, 3);

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div className="space-y-1">
          <h1 className="text-3xl font-extrabold tracking-tight">Welcome back, {user?.displayName?.split(' ')[0]}!</h1>
          <p className="text-zinc-500">Here's your productivity overview for today.</p>
        </div>
        <Link to="/tasks">
            <Button>
                View All Tasks
                <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
        </Link>
      </div>

      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="border-none bg-zinc-900 text-white shadow-xl shadow-zinc-900/20">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-bold uppercase tracking-widest text-zinc-400">Completion Rate</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-4xl font-extrabold">{stats.completionRate}%</div>
            <div className="mt-4 h-1.5 w-full rounded-full bg-zinc-800">
              <motion.div 
                className="h-full rounded-full bg-white" 
                initial={{ width: 0 }}
                animate={{ width: `${stats.completionRate}%` }}
                transition={{ duration: 1, ease: "easeOut" }}
              />
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-zinc-500">
               <Activity className="h-4 w-4 text-orange-500" />
               Pending Tasks
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-4xl font-extrabold text-zinc-900">{stats.pending}</div>
            <p className="mt-1 text-xs text-zinc-400">Tasks requiring attention</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-zinc-500">
               <CheckSquare className="h-4 w-4 text-green-500" />
               Completed
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-4xl font-extrabold text-zinc-900">{stats.completed}</div>
            <p className="mt-1 text-xs text-zinc-400">Tasks finished total</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-zinc-500">
               <AlertCircle className="h-4 w-4 text-zinc-400" />
               Activity Scale
            </CardTitle>
          </CardHeader>
          <CardContent className="flex items-end justify-between">
            <div className="flex gap-1">
                {[4, 7, 3, 8, 5, 9, 6].map((h, i) => (
                    <div key={i} className="w-2 rounded-t-sm bg-zinc-100" style={{ height: `${h * 4}px` }} />
                ))}
            </div>
            <div className="text-right">
                <span className="text-xs font-bold text-zinc-400">ACTIVE</span>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-8 lg:grid-cols-5">
        <div className="lg:col-span-3 space-y-6">
            <h3 className="text-lg font-bold">Recent Tasks</h3>
            <div className="grid gap-4">
                {recentTasks.map((task) => (
                    <Card key={task.id} className="transition-all hover:translate-x-1 hover:border-zinc-200">
                        <CardContent className="flex items-center justify-between p-5">
                            <div className="flex items-center gap-4">
                                <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${task.completed ? 'bg-green-50 text-green-600' : 'bg-zinc-100 text-zinc-500'}`}>
                                    <CheckSquare className="h-5 w-5" />
                                </div>
                                <div>
                                    <h4 className={`font-bold ${task.completed ? 'text-zinc-400 line-through' : 'text-zinc-900'}`}>{task.title}</h4>
                                    <div className="flex items-center gap-3 text-xs text-zinc-400">
                                        <span className="flex items-center gap-1"><Clock className="h-3 w-3" /> Recently updated</span>
                                    </div>
                                </div>
                            </div>
                            <Link to="/tasks">
                                <Button variant="ghost" size="sm">Edit</Button>
                            </Link>
                        </CardContent>
                    </Card>
                ))}
                {recentTasks.length === 0 && (
                     <div className="rounded-3xl border border-dashed border-zinc-200 p-12 text-center">
                        <CheckSquare className="mx-auto h-12 w-12 text-zinc-200" />
                        <p className="mt-4 font-medium text-zinc-500">Your task list is empty</p>
                        <Link to="/tasks" className="mt-2 inline-block text-sm text-zinc-900 underline underline-offset-4">Create your first task</Link>
                    </div>
                )}
            </div>
        </div>

        <div className="lg:col-span-2 space-y-6">
             <h3 className="text-lg font-bold">Schedule</h3>
             <Card className="overflow-hidden border-none shadow-xl shadow-zinc-200/50">
                <CardHeader className="bg-zinc-900 py-3">
                    <CardTitle className="text-xs font-bold uppercase tracking-widest text-zinc-400">Quick Calendar</CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                    <div className="grid grid-cols-7 gap-2">
                        {['S','M','T','W','T','F','S'].map((d, i) => (
                            <div key={`${d}-${i}`} className="text-center text-[10px] font-bold text-zinc-400">{d}</div>
                        ))}
                        {Array.from({ length: 31 }).map((_, i) => (
                            <div 
                                key={i} 
                                className={`flex h-8 w-8 items-center justify-center rounded-lg text-xs font-medium ${
                                    i + 1 === 26 ? 'bg-zinc-900 text-white' : 'text-zinc-500 hover:bg-zinc-100'
                                }`}
                            >
                                {i + 1}
                            </div>
                        ))}
                    </div>
                </CardContent>
             </Card>
        </div>
      </div>
    </div>
  );
};
