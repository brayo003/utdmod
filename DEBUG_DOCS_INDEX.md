# Debug Testing Documentation Index

All documentation files for the debug build are listed below. **Start with `DEBUG_READY.md`**.

---

## 📋 Main Documents

### 1. **DEBUG_READY.md** ⭐ START HERE
**What**: Executive summary of debug build status and quick start guide.  
**Read if**: You want a 5-minute overview before testing.  
**Key sections**:
- Status checklist (✅ All green)
- Quick Start (3 steps)
- Expected behavior by timeline
- Diagnostic decision tree

---

### 2. **DEBUG_BUILD_SUMMARY.md**
**What**: Complete technical summary of all changes applied.  
**Read if**: You need to understand exactly what was changed and why.  
**Key sections**:
- 6 changes explained with code snippets
- Before/after tables
- Expected console output
- Files modified (4 total)

---

### 3. **QUICK_DEBUG_CHECKLIST.md**
**What**: One-page copy/paste reference of what to look for in logs.  
**Read if**: You're actively testing and need quick diagnosis.  
**Key sections**:
- 5 log patterns (one-line syntax)
- Visual proof checklist (in-game)
- One-line troubleshooting table
- Expected timeline (0:00 to 5:05)

---

### 4. **DEBUG_TESTING_GUIDE.md**
**What**: Detailed testing guide with phase-by-phase instructions.  
**Read if**: You're doing comprehensive testing or hit a roadblock.  
**Key sections**:
- 4 testing phases (sync, mining, escalation, persistence)
- Diagnostic output samples (healthy vs broken)
- Common issues & fixes (table)
- Tuning notes for production

---

### 5. **REVERT_DEBUG_CHANGES.md**
**What**: Step-by-step checklist to remove all debug changes after testing.  
**Read if**: Debug testing is done and you need to return to production.  
**Key sections**:
- 4 files with exact diffs
- Line numbers for each change
- Verification commands
- Risk assessment (low)

---

## 🚀 How to Use

### Scenario A: "I want to start testing NOW"
1. Read `DEBUG_READY.md` (5 min)
2. Run `./gradlew :runServer`
3. Reference `QUICK_DEBUG_CHECKLIST.md` while testing
4. If confused, jump to `DEBUG_TESTING_GUIDE.md` Phase 1

### Scenario B: "I need to understand what changed"
1. Read `DEBUG_BUILD_SUMMARY.md` (10 min)
2. Skim `QUICK_DEBUG_CHECKLIST.md` (2 min)
3. Reference the code snippets while reviewing source

### Scenario C: "Something doesn't match expected behavior"
1. Open `QUICK_DEBUG_CHECKLIST.md` (find your symptom)
2. Jump to matching row in "One-Line Troubleshooting"
3. If still unclear, read full section in `DEBUG_TESTING_GUIDE.md`

### Scenario D: "Testing is done, time to clean up"
1. Open `REVERT_DEBUG_CHANGES.md`
2. Follow the 4-file checklist
3. Run `./gradlew :compileJava -q`
4. Run test scenarios to confirm

---

## 📊 Quick Reference

### Files Modified
```
UTDModClient.java          → Added debug logging (frameCount, [CLIENT_DEBUG])
ChunkTensionManager.java   → Mining injection × 25
ChunkTensionData.java      → Thresholds lowered + NBT logging
TensionHud.java (client)   → Red bar override for visibility
```

### Values Changed
| Parameter | Debug | Production |
|-----------|-------|-----------|
| Mining multiplier | 25x | 1x |
| T1 (STRAINED) | 0.08 | 0.70 |
| T2 (FRACTURED) | 0.18 | 1.40 |
| T3 (DECOUPLED) | 0.40 | 3.0 |

### Log Patterns to Watch For
```
[UTDMod] Global Tension: X.XX          → Server PDE
[CLIENT_DEBUG] perceived=X.XXXXXX      → Client sync
[HUD_DEBUG] TENSION_DETECTED t=X.XX    → Mining injection
[NBT_WRITE] chunk (X,Z) tension=X.XX   → Persistence save
[NBT_READ] chunk (X,Z) tension=X.XX    → Persistence load
```

---

## ✅ Verification Checklist

Before starting tests:
- [ ] `DEBUG_READY.md` shows "✅ BUILD SUCCESSFUL"
- [ ] `./gradlew :compileJava -q` completes with no errors
- [ ] All 4 files modified (check with `git status` or `find src -newer`)

After testing:
- [ ] Red bar appeared in-game when mining
- [ ] Console showed `[HUD_DEBUG]` output
- [ ] State progressed STABLE → STRAINED → FRACTURED
- [ ] NBT_WRITE and NBT_READ matched values

---

## 🔧 Common Tasks

### "Where do I start testing?"
→ Start with `DEBUG_READY.md` → Quick Start section

### "My red bar never appeared, what do I do?"
→ `QUICK_DEBUG_CHECKLIST.md` → "What You See" table → Row 3

### "I need to understand mining injection"
→ `DEBUG_BUILD_SUMMARY.md` → Section 2 → Mining Injection Table

### "How do I remove all debug changes?"
→ `REVERT_DEBUG_CHANGES.md` → Follow 4-file checklist

### "Where's the decision tree for diagnostics?"
→ `DEBUG_READY.md` → "Diagnostic Decision Tree" OR `QUICK_DEBUG_CHECKLIST.md`

---

## 📝 File Sizes & Read Times

| Document | Size | Read Time | Best For |
|----------|------|-----------|----------|
| DEBUG_READY.md | ~3KB | 5 min | Quick overview |
| DEBUG_BUILD_SUMMARY.md | ~5KB | 10 min | Understanding changes |
| QUICK_DEBUG_CHECKLIST.md | ~4KB | 3 min | Active testing |
| DEBUG_TESTING_GUIDE.md | ~8KB | 15 min | Detailed reference |
| REVERT_DEBUG_CHANGES.md | ~4KB | 8 min | Cleanup |

---

## 🎯 Success Criteria

✅ **Testing successful if**:
1. Red bar appears in HUD after 5 stone breaks
2. Console shows both `[HUD_DEBUG]` and `[CLIENT_DEBUG]` output
3. Action bar displays state transitions (STABLE → STRAINED → FRACTURED)
4. NBT_READ ≈ NBT_WRITE after save/load cycle
5. All test scenarios pass after reversion

❌ **Testing failed if**:
- Red bar never appears (even after 10+ stone breaks)
- No `[HUD_DEBUG]` or `[CLIENT_DEBUG]` output
- State never progresses beyond STABLE
- NBT_READ shows 0 after NBT_WRITE showed non-zero
- Test scenarios fail after reversion

---

## 🚨 Critical Reminders

⚠️ **These are debug values, NOT production**
- Mining 25x boost is temporary testing only
- Thresholds are diagnostic, will be reverted
- All console prints will be removed

⚠️ **Don't share this build**
- Only for local testing and diagnostics
- Follow REVERT_DEBUG_CHANGES.md before committing

⚠️ **Test all 4 areas**
- Sync (client reads tension)
- Mining (injection works)
- Escalation (states progress)
- Persistence (saves survive load)

---

## 📞 Troubleshooting Fast Path

**"Red bar works, but tension vanishes when I move away"**
→ This is persistence testing
→ Check `DEBUG_TESTING_GUIDE.md` Phase 4
→ Look for `[NBT_READ]` mismatch

**"Console shows mining but HUD is blank"**
→ HUD registration issue
→ Check `TensionHud.render()` in `DEBUG_BUILD_SUMMARY.md` section 4
→ Ensure it's registered in client initializer

**"Everything works but test scenarios fail"**
→ This is expected after revert
→ Follow `REVERT_DEBUG_CHANGES.md` step-by-step
→ Verify all lines removed
→ Re-run scenarios

---

## 📚 Related Documentation

Original system references (not debug):
- `/docs/PROJECT_COMPLETE_REFERENCE.md` — Full PDE spec
- `/docs/system_audit/SYSTEM_OVERVIEW.md` — Architecture
- `/docs/PIPELINE_TRACE.md` — Data flow

---

**BUILD STATUS: ✅ READY FOR TESTING**

Start with `DEBUG_READY.md` and follow the links. Good luck!
