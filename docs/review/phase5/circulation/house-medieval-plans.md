# 5.11 ground-floor plans, house-medieval (before and after)

Legend: `#` wall, `D` door, `.` archway (open), `s` stair, letters are the room use (b bedroom, k kitchen, l living, e entry, h hall). Generated from the batch JSON (`seed-N.json`) by a throwaway script; "before" is the Phase 4 generator (base 9c5aab7), "after" is this branch.

## seed 7

Before:
```
b=bedroom k=kitchen l=living e=entry
                  
                  
#            #D###
 bbb#kkkk#lll#eee 
 bbb#kkkk#lll#eee 
 bbb#kkkkDlll#eee 
 bbbDkkkk#lllDeee 
 bbb#kkkk#lll#eee 
##D###     ###D###
 bbbb       bbbbb 
 bbbb       bbbbb 
 bbbb#     #bbbbb 
 bbbb#bbbbb#bbbbb 
 bbbbDbbbbbDbbbbb 
 bbbb#bbbbb#bbbbb 
 bbbb#bbbbb#bbbbb 
#    #     #     #
                  
```
After:
```
l=living k=kitchen b=bedroom
                  
                  
#            #D###
 llllll#kkkk#llll 
 llllll#kkkkDllll 
 llllll#kkkk#llll 
 llllll.kkkk#llll 
 llllll#kkkk#llll 
##D###     ###D###
 bbbb       bbbbb 
 bbbb       bbbbb 
 bbbb#     #bbbbb 
 bbbb#bbbbb#bbbbb 
 bbbbDbbbbbDbbbbb 
 bbbb#bbbbb#bbbbb 
 bbbb#bbbbb#bbbbb 
#    #     #     #
                  
```

## seed 3

Before:
```
e=entry l=living b=bedroom k=kitchen
                  
##D#       #      
 eee#llllll       
 eee#llllll       
 eee#llllll       
 eeeDllllll       
# ###D###  #      
   bbbbb          
   bbbbb          
   bbbbb          
   ###D#          
   kkkkk          
   kkkkk          
   kkkkk          
   kkkkk          
  #     #         
                  
                  
```
After:
```
e=entry h=hall k=kitchen
                  
##D#       #      
 eee#hhhhhh       
 eee#hhhhhh       
 eee#hhhhhh       
 eeeDhhhhhh       
# ###D###  #      
   hhhhh          
   hhhhh          
   hhhhh          
   ###.#          
   kkkkk          
   kkkkk          
   kkkkk          
   kkkkk          
  #     #         
                  
                  
```

## seed 9

Before:
```
l=living b=bedroom k=kitchen
                  
    #          D##
     lll#bbb#llll 
     lll#bbb#llll 
     lllDbbb#llll 
     lll#bbbDllll 
     #D###D#####D 
     bbb#kkk#bbbb 
     bbb#kkkDbbbb 
     bbb#kkk#bbbb 
     bbb#kkk#bbbb 
    #            #
                  
                  
                  
                  
                  
                  
```
After:
```
l=living b=bedroom k=kitchen
                  
    #          D##
     lll#llllllll 
     lll#llllllll 
     lll#llllllll 
     lllDllllllll 
     lll##D#####D 
     lll#bbb#kkkk 
     lll#bbb#kkkk 
     lll#bbb#kkkk 
     lll#bbb#kkkk 
    #            #
                  
                  
                  
                  
                  
                  
```

## seed 4

Before:
```
b=bedroom l=living
                  
                  
  #            D##
   bbbbb#llllllll 
   bbbbbDllllllll 
   bbbbb#llllllll 
  ##D###    ##D###
   bbbb      llll 
   bbbb      llll 
   bbbb      llll 
   bbbb      llll 
  #    #    #    #
                  
                  
                  
                  
                  
                  
```
After:
```
h=hall l=living b=bedroom
                  
                  
  #            D##
   hhhhh#llllllll 
   hhhhhDllllllll 
   hhhhh#llllllll 
  ##D###    ##D###
   bbbb      llll 
   bbbb      llll 
   bbbb      llll 
   bbbb      llll 
  #    #    #    #
                  
                  
                  
                  
                  
                  
```

## seed 12

Before:
```
l=living
                  
      #  #D###    
       llllll     
       llllll     
       llllll     
       ##D###     
       llllll     
       llllll     
       llllll     
       llllll     
      #      #    
                  
                  
                  
                  
                  
                  
                  
```
After:
```
l=living
                  
      #  #D###    
       llllll     
       llllll     
       llllll     
       ##D###     
       llllll     
       llllll     
       llllll     
       llllll     
      #      #    
                  
                  
                  
                  
                  
                  
                  
```

# After CIRC2 (squarer rooms, bedrooms first)

Same lot and seeds (house-medieval, 20x20, `noise:7:64:6:32`), ground floor, generated with `claude/p5-circ2`. Seed 9 had a long skinny living room and one small bedroom (the "after" above); now the main living room is a 4x8 block and there are five bedrooms and a kitchen. Seed 7 keeps its row of wing bedrooms (each wing is planned on its own and 80 % of lone wing rooms become bedrooms, a seeded 20 % become the kitchen or living room instead). Cutaway galleries: `house-*-cutaway-after-circ2.png`.

### seed 9

Ground floor:
```
k=kitchen b=bedroom l=living
           D  
 kkk#llllllll 
 kkk#llllllll 
 kkkDllllllll 
 kkk#llllllll 
 #D# #D# ###D 
 bbb#bbb#bbbb 
 bbb#bbb#bbbb 
 bbbDbbb#bbbb 
 bbb#bbb#bbbb 
              
```

### seed 7

Ground floor:
```
l=living b=bedroom
              D   
 llllll#bbbb#llll 
 llllll#bbbbDllll 
 llllll#bbbb#llll 
 llllllDbbbb#llll 
 llllll#bbbb#llll 
 #D##        #D## 
 bbbb       bbbbb 
 bbbb       bbbbb 
 bbbb       bbbbb 
 bbbb#bbbbb#bbbbb 
 bbbbDbbbbbDbbbbb 
 bbbb#bbbbb#bbbbb 
 bbbb#bbbbb#bbbbb 
                  
```



## After house program (p5-house-program)

Whole-house pass: one kitchen and one living room across all wings. Generated from the `house-medieval` gallery scenes by a throwaway script.

### seed 7
```
l=living k=kitchen b=bedroom
 ###### #### #D## 
#llllll#kkkk#llll#
#llllll#kkkkDllll#
#llllll#kkkk#llll#
#llllll.kkkk#llll#
#llllll#kkkk#llll#
 #D#### ######D## 
#bbbb#     #bbbbb#
#bbbb#     #bbbbb#
#bbbb#######bbbbb#
#bbbb#bbbbb#bbbbb#
#bbbbDbbbbbDbbbbb#
#bbbb#bbbbb#bbbbb#
#bbbb#bbbbb#bbbbb#
 #### ##### ##### 
```

### seed 9
```
k=kitchen b=bedroom l=living
 ### ######D# 
#kkk#llllllll#
#kkk#llllllll#
#kkkDllllllll#
#kkk#llllllll#
 #D# #D#####D 
#bbb#bbb#bbbb#
#bbb#bbb#bbbb#
#bbbDbbb#bbbb#
#bbb#bbb#bbbb#
 ### ### #### 
```

## After open plan (p5-openplan)

Style `interior.openPercent` (medieval 20, taiga 35, snowy 40, desert 60, plains 65, jungle 70, plain 85): kitchen links open by that chance, other public pairs at least 40 %; from 60 openings are 2 to 4 wide; wings joined across a strip wall can be archways too. `.` is open, `D` a door. Generator: house 20x20, noise terrain, front north (the gallery preset).

### medieval seed 7
```
##############D###
#llllll#kkkk#llll#
#llllll#kkkkDllll#
#llllll#kkkk#llll#
#llllll.kkkk#llll#
#llllll#kkkk#llll#
##D###########D###
#bbbb#     #bbbbb#
#bbbb#     #bbbbb#
#bbbb#######bbbbb#
#bbbb#bbbbb#bbbbb#
#bbbbDbbbbbDbbbbb#
#bbbb#bbbbb#bbbbb#
#bbbb#bbbbb#bbbbb#
##################
```

### plain seed 6
```
   #####D#####
   #kkk#lllll#
   #kkk.lllll#
   #kkk.lllll#
   #kkk.lllll#
   #kkk.lllll#
   #kkk#lllll#
   #kkk#lllll#
   #####D#####
     #bbbbb#
     #bbbbb#
     #bbbbb#
     #bbbbb#
     #bbbbb#
     #bbbbb#
     #######
```

### plain seed 2
```
##############D##
#kkk#llllll#llll#
#kkk.llllll#llll#
#kkk.llllll.llll#
#kkk.llllll.llll#
#kkk#llllll#llll#
#kkk#llllll#llll#
############llll#
           #llll#
           #llll#
           ######
```

### plain seed 14
```
    #####D#####
    #kkk#lllll#
    #kkk#lllll#
    #kkk#lllll#
    #kkk.lllll#
    #kkk.lllll#
    #kkk.lllll#
    #kkk#lllll#
    ###########
```

Cutaway galleries (16 seeds): `house-medieval-cutaway-open-plan.png`, `house-plain-cutaway-open-plan.png`.
