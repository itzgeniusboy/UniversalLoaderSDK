# Deployment Guide for OneCore SDK Engine

This guide explains how to deploy this project to GitHub and automate the build process to generate your `.aar` library.

## Step 1: Prepare GitHub Repository
1. Create a new **public** repository on GitHub named `OneCore`.
2. Do **not** initialize with a README or .gitignore (we have our own).

## Step 2: Upload Files
You can use the GitHub Web Interface or Git CLI:

### Option A: Web Interface (Easiest)
1. In your new repo, click "uploading an existing file".
2. Drag and drop **all files and folders** from this project.
3. Commit directly to the `main` branch.

### Option B: Git CLI
```bash
git init
git add .
git commit -m "Initial SDK Build"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/OneCore.git
git push -u origin main
```

## Step 3: Trigger Automations
1. Once pushed, click the **Actions** tab in your GitHub repository.
2. You will see a workflow named **"Build Android SDK"** running.
3. Wait approximately 1-2 minutes for completion.

## Step 4: Download the SDK
1. When the build finishes (green checkmark), click on the successful run.
2. Scroll down to the **Artifacts** section.
3. Click on `onecore-v1.0.4` to download the zip containing your `.aar` file.

## Step 5: Integration
Copy the downloaded `.aar` file into your Android project's `libs/` folder and update your `build.gradle` (see README.md for details).
