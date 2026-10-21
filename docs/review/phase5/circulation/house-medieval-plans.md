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
