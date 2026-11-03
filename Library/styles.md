---
tags: meta/style
---

Kind of a hack - there should be a way to not use important but it’s straight forward. The table defaults in SilverBullet suck.
```space-style
tbody td { white-space: wrap !important; }
thead td { white-space: nowrap !important ;}

#sb-main .cm-editor .cm-content {
  --editor-width: unset;
  max-width: unset;
  padding: 5px 2em;
}

```
