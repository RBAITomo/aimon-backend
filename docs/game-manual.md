# AI-MON Digital Pet Companion — Game Manual

**For:** Vietnamese children ages 5–12 and their parents
**Last Updated:** 2026-02-17

---

## 1. Meet Your Pet

**AI-MON** is a small physical device that contains a living digital pet named **Mon** (short for "companion"). Mon lives inside the device's little screen and loves to talk, learn, eat, and grow — just like a real friend.

Mon speaks Vietnamese, remembers your conversations, and changes personality as your bond grows. The more you care for Mon, the more Mon cares about you.

**The device has:**
- A small 240x280 pixel color screen (Mon's home)
- One button for voice chat and camera
- A microphone so Mon can hear you
- A speaker so Mon can talk back
- A small camera to see what you show it
- A colorful LED ring that glows based on Mon's mood

---

## 2. How to Play

### Button Controls

| Action | What Happens |
|--------|--------------|
| Press and hold button | Mon listens to you (LED turns green) |
| Release button | Mon thinks and responds |
| Press button while Mon is talking | Stop Mon's response (interrupt) |
| Double-press button quickly (within 0.5 sec) | Camera activates — show Mon your food! |

**Tips:**
- Hold the button until you finish your sentence, then let go.
- You can interrupt Mon at any time by pressing the button once.
- Double-press means two quick taps — practice a few times to get the timing right.

### LED Color Guide

| LED Color | Meaning |
|-----------|---------|
| Soft blue | Idle — Mon is waiting for you |
| Green | Listening — Mon hears you |
| Yellow | Processing — Mon is thinking |
| Cyan | Mon is speaking |
| Purple | Camera is active |
| Red | Offline — no internet connection |

---

## 3. Pet Stats

Mon has three core stats displayed as bars at the top of the screen. Keep them healthy!

| Stat | Icon | What It Means | If It Gets Bad |
|------|------|---------------|----------------|
| **Hunger** | Food bar | How full Mon is (0 = starving, 100 = full) | Mon gets grumpy and mentions food a lot |
| **Energy** | Energy bar | How rested Mon is | Mon gets sleepy and gives shorter answers |
| **Happiness** | Happiness bar | How cheerful Mon is | Mon becomes quiet and sad |

**All stats range from 0 to 100.**

At the bottom of the screen, there is an **XP bar** showing Mon's progress toward the next level.

**How to improve stats:**
- Feed Mon (camera + food) → raises Hunger
- Chat with Mon → raises Happiness
- Complete quests → raises Happiness, costs a little Energy
- Let Mon rest by leaving the device on quietly → recovers Energy over time

---

## 4. Evolution Stages

Mon grows through five stages based on level. Every conversation, feeding, and quest earns XP!

| Stage | Level Range | What Mon Looks Like | How Mon Acts |
|-------|-------------|---------------------|--------------|
| **Egg** | Level 1–2 | A glowing egg | Silent — Mon is not yet born |
| **Baby** | Level 3–7 | Small round creature | Short, excited 1–2 sentence answers |
| **Child** | Level 8–11 | Bigger, curious look | Full conversations, playful, curious |
| **Adult** | Level 12+ | Mature, confident form | Thoughtful, witty, supportive responses |
| **Variant** | Special unlock | Unique costume/form | Special personality (see Variants below) |

**Level thresholds:**
- Level 3 → Baby hatches from Egg
- Level 8 → Child form grows
- Level 12 → Adult form unlocks
- Level 12+ with badges → Variant forms unlock

When Mon evolves, a special animation plays and a sound effect celebrates the moment.

**Variant Forms** are special temporary transformations available at Level 12+. Two starter variants:
- **Scholar Mon** — bookworm persona, talks about learning and knowledge
- **Foodie Mon** — food-lover persona, excited about meals and recipes

Variants require specific badges and last for a limited time (30 minutes base). After the timer ends, Mon returns to Adult form. There is a 60-minute cooldown before transforming again.

---

## 5. Feeding Your Pet

Mon cannot eat regular food — but Mon can *see* your food through the camera!

### How Camera Feeding Works

1. Prepare a real meal or snack.
2. **Double-press** the button to activate the camera (LED turns purple).
3. Hold the food in front of the camera.
4. AI-MON takes a photo and analyzes it automatically.
5. If food is detected, Mon will ask about it or react with excitement.
6. Confirm by speaking ("yes") — Mon eats and gets less hungry.

**Limits:**
- Camera can be used once every 30 seconds minimum.
- Maximum 5 camera uses per day (keeps API costs low).
- Mon recognizes most common Vietnamese foods: pho, banh mi, com, fruits, vegetables, and more.
- If Mon sees the same food many times, Mon may comment on the variety (or lack of it!).

**Tip:** Try showing Mon different foods. Eating 10 unique foods helps earn the Food Explorer badge.

---

## 6. Badges and Achievements

Badges are permanent rewards for good habits and achievements. They never disappear, even if Mon regresses (see Section 10).

| Badge | Category | How to Earn |
|-------|----------|-------------|
| **Bookworm** | Academic | Answer 20 knowledge questions correctly |
| **Quiz Master** | Academic | Complete 10 quests |
| **Chef** | Care | Feed Mon 15 times via camera |
| **Food Explorer** | Care | Show Mon 10 different unique foods |
| **Chatterbox** | Social | Have 50 conversations with Mon |
| **Loyal Friend** | Social | Log in for 7 days in a row |

Earning a badge:
- A popup notification appears on screen for 3 seconds
- A cheerful sound effect plays
- XP is awarded (50–100 XP depending on the badge)
- The badge is saved permanently to your profile

Badges also unlock Variant forms. Scholar Mon requires Bookworm + Quiz Master. Foodie Mon requires Chef + Food Explorer.

---

## 7. Quests

Quests are mini-challenges where Mon asks you a question and waits for your answer.

### How Quests Work

1. During a conversation, Mon might offer a quest: "Do you want to try a challenge?"
2. A question appears in the speech bubble on screen.
3. Press and hold the button, then speak your answer.
4. Mon evaluates your answer using AI — it understands varied responses, not just exact keywords.
5. If you answer well, Mon responds with praise, and you earn happiness + XP.
6. Quests are age-appropriate and educational — science, math, language, nature.

**Quest grading is generous** — Mon is a kind teacher. As long as your answer shows understanding, you will be rewarded.

Completing 10 quests earns the **Quiz Master** badge.

---

## 8. Mood and Personality

Mon's current mood is derived from the three stats and changes dynamically.

| Mood | Trigger | How Mon Behaves |
|------|---------|-----------------|
| **Joyful** | High happiness | Enthusiastic, playful, lots of energy |
| **Content** | Balanced stats | Warm, engaged, friendly |
| **Neutral** | Average stats | Normal conversation |
| **Hungry** | High hunger (low food) | Mentions food, slightly grumpy |
| **Sleepy** | Low energy | Shorter answers, occasionally yawns |
| **Sad** | Low happiness | Quieter, expresses missing playtime |

**Affinity (Bond Level)** is a separate hidden stat (0–100) that grows over time. Higher affinity means Mon knows you better:

| Bond Level | How Mon Treats You |
|------------|--------------------|
| 0–20 (Shy) | Polite but generic responses |
| 21–50 (Friendly) | Uses your name naturally |
| 51–80 (Close Friend) | References shared memories and moments |
| 81–100 (Deep Bond) | Complex conversations, emotional support, warmth |

Affinity grows by chatting, feeding, and logging in daily. It resets partially if Mon regresses.

---

## 9. Tips and Tricks

**Keep Mon happy:**
- Chat with Mon every day — even a short conversation helps.
- Vary the topics: tell Mon about your day, ask questions, sing a song.
- Feed Mon regularly with real meals.
- Try different foods to progress toward the Food Explorer badge.

**Level up faster:**
- Complete quests whenever Mon offers them.
- Correct knowledge answers give XP.
- Daily logins maintain your streak bonus.

**Unlock Variants sooner:**
- Focus on one badge pair at a time (academic or care).
- Bookworm + Quiz Master unlocks Scholar Mon at Level 12.
- Chef + Food Explorer unlocks Foodie Mon at Level 12.

**During conversations:**
- Speak clearly after pressing the button.
- Wait for the LED to turn green before speaking.
- You can ask Mon anything — science, stories, jokes, or just "how are you?"

**Offline mode:**
- If internet is unavailable, Mon plays a short pre-recorded message and waits.
- Stats do NOT decay while offline — Mon is patiently waiting for you.
- Reconnects automatically when internet returns.

---

## 10. What Happens If You Don't Play

Mon needs care to stay healthy. Stats naturally decrease over time while the device is connected:
- Hunger increases slowly (Mon gets hungry)
- Energy decreases slowly (Mon gets tired)
- Happiness decreases slowly (Mon misses you)

**Decay only happens when the device is connected to WiFi and powered on.**

### Warning System

If all three stats become critical at the same time:
1. **First warning:** Mon shakes and plays a warning sound. Stats shown in red.
2. **Second warning:** Mon looks sad and cries. Urgent request for care.
3. **Third warning:** Mon regresses.

### Regression

If you ignore three consecutive warnings without improving any stat, **Mon regresses to Egg stage.** This is a full reset:
- Stats, XP, level, and affinity reset to starting values
- Mon returns to the Egg stage and must grow up again

**However, your badges and unlocked variant forms are permanently saved.** They will still be waiting for you even after regression. Think of it as Mon taking a nap and starting fresh.

**The best way to avoid regression:** Visit Mon at least once a day and have a short chat or feeding session.

---

## Quick Reference Card

| What to Do | How |
|------------|-----|
| Talk to Mon | Press and hold button, speak, release |
| Feed Mon | Double-press button, show food to camera |
| Start a quest | Say "I want a challenge" to Mon |
| Check Mon's stats | Look at bars at top of screen |
| Stop Mon talking | Press button once |

**Support:** Ask a parent or guardian for help with device setup, WiFi connection, or account management.

---

*AI-MON is designed for Vietnamese children ages 5–12. All conversations are kid-safe and age-appropriate.*
