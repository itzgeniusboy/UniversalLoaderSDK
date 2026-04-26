import React, { useState, useEffect } from 'react';
import { useAuth } from '../lib/auth';
import { subscribeToTasks, Task, updateTask, deleteTask, addTask } from '../services/taskService';
import { Card, CardContent, CardHeader, CardTitle } from './ui/Card';
import { Button } from './ui/Button';
import { Input, Checkbox } from './ui/Input';
import { Plus, Trash2, Calendar, Clock, Filter, Search } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

export const TaskList = () => {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<'all' | 'active' | 'completed'>('all');

  useEffect(() => {
    if (user) {
      const unsubscribe = subscribeToTasks(user.uid, (data) => {
        setTasks(data);
      });
      return unsubscribe;
    }
  }, [user]);

  const handleAddTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskTitle.trim()) return;
    await addTask({
      title: newTaskTitle,
      description: '',
      completed: false,
      dueDate: null,
    });
    setNewTaskTitle('');
  };

  const filteredTasks = tasks.filter(task => {
    const matchesSearch = task.title.toLowerCase().includes(searchQuery.toLowerCase());
    if (filter === 'active') return matchesSearch && !task.completed;
    if (filter === 'completed') return matchesSearch && task.completed;
    return matchesSearch;
  });

  const stats = {
    total: tasks.length,
    completed: tasks.filter(t => t.completed).length,
    active: tasks.filter(t => !t.completed).length,
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card className="bg-white">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-zinc-500 uppercase tracking-wider">Total Tasks</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.total}</div>
          </CardContent>
        </Card>
        <Card className="bg-white text-green-600">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-green-500/70 uppercase tracking-wider">Completed</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.completed}</div>
          </CardContent>
        </Card>
        <Card className="bg-white text-zinc-900">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-zinc-500 uppercase tracking-wider">Remaining</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{stats.active}</div>
          </CardContent>
        </Card>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <form onSubmit={handleAddTask} className="flex flex-1 gap-2">
          <Input 
            placeholder="Add a new task..." 
            value={newTaskTitle}
            onChange={(e) => setNewTaskTitle(e.target.value)}
            className="h-12 text-base shadow-sm"
          />
          <Button type="submit" className="h-12 px-6">
            <Plus className="mr-2 h-5 w-5" />
            Add
          </Button>
        </form>
      </div>

      <Card className="overflow-hidden border-zinc-200 shadow-xl shadow-zinc-200/20">
        <CardHeader className="border-b border-zinc-100 bg-zinc-50/50 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
                <Input 
                  placeholder="Search tasks..." 
                  className="w-48 pl-10 h-9 bg-white" 
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              <div className="flex rounded-lg border border-zinc-200 bg-white p-1">
                {(['all', 'active', 'completed'] as const).map((f) => (
                  <button
                    key={f}
                    onClick={() => setFilter(f)}
                    className={`rounded-md px-3 py-1 text-xs font-semibold capitalize transition-all ${
                      filter === f 
                      ? 'bg-zinc-900 text-white shadow-sm' 
                      : 'text-zinc-500 hover:text-zinc-900'
                    }`}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>
            <div className="text-xs text-zinc-400 font-medium">
              Showing {filteredTasks.length} tasks
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-zinc-100">
            <AnimatePresence mode="popLayout" initial={false}>
              {filteredTasks.length > 0 ? (
                filteredTasks.map((task) => (
                  <motion.div
                    key={task.id}
                    layout
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="group flex items-center justify-between p-4 transition-colors hover:bg-zinc-50"
                  >
                    <div className="flex items-center gap-4">
                      <Checkbox 
                        checked={task.completed} 
                        onChange={() => updateTask(task.id!, { completed: !task.completed })}
                        className="h-5 w-5 rounded-md border-2"
                      />
                      <div>
                        <p className={`font-medium transition-all ${task.completed ? 'text-zinc-400 line-through' : 'text-zinc-900'}`}>
                          {task.title}
                        </p>
                        {task.dueDate && (
                          <div className="mt-1 flex items-center gap-1.5 text-xs text-zinc-400">
                            <Calendar className="h-3 w-3" />
                            {task.dueDate.toDate().toLocaleDateString()}
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <Button 
                        variant="ghost" 
                        size="sm" 
                        className="h-8 w-8 p-0 text-zinc-400 hover:text-red-600 hover:bg-red-50"
                        onClick={() => deleteTask(task.id!)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </motion.div>
                ))
              ) : (
                <div className="flex flex-col items-center justify-center p-12 text-center">
                  <div className="mb-4 rounded-full bg-zinc-100 p-4">
                    <Clock className="h-8 w-8 text-zinc-300" />
                  </div>
                  <p className="text-lg font-medium text-zinc-500">No tasks found</p>
                  <p className="max-w-[200px] text-sm text-zinc-400">Get started by creating your first task above.</p>
                </div>
              )}
            </AnimatePresence>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
