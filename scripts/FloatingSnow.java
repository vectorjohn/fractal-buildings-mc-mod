/** Counts snow layers with air under them in a saved world (ROADCLEAR 2026-10-27): {@code nix develop -c bash -c "cd scripts && javac -d /tmp/fs McaTop.java FloatingSnow.java && java -cp /tmp/fs FloatingSnow REGION_DIR"}. Prints the histogram of the air run under each and, for those over road blocks, samples. */
import java.nio.file.*; import java.util.*;
public class FloatingSnow {
  static boolean air(String n){return n.equals("air")||n.equals("cave_air");}
  public static void main(String[] a) throws Exception { Path dir=Path.of(a[0]); int tot=0,chunks=0; TreeMap<String,Integer> pc=new TreeMap<>(); TreeMap<String,Integer> hist=new TreeMap<>(); List<String> s=new ArrayList<>();
    for (Path f: Files.list(dir).toList()) { String[] p=f.getFileName().toString().split("\\."); int rx=Integer.parseInt(p[1]), rz=Integer.parseInt(p[2]);
      for(int cz=rz*32;cz<rz*32+32;cz++)for(int cx=rx*32;cx<rx*32+32;cx++){ int[] mn={0}; String[] bl; try{bl=McaTop.blocks(dir,cx,cz,mn);}catch(Exception e){continue;} if(bl==null)continue; chunks++; int H=bl.length/256;
        for(int y=1;y<H;y++)for(int i=0;i<256;i++){ if(!bl[y*256+i].equals("snow"))continue; String b=bl[(y-1)*256+i]; if(air(b)){ tot++; int lz=i/16,lx=i%16; // depth of air below
            int d=0; while(y-1-d>=0&&air(bl[(y-1-d)*256+i]))d++; String below=y-1-d>=0?bl[(y-1-d)*256+i]:"?"; hist.merge("air-run "+(d>8?">8":d)+" over "+(below.contains("planks")||below.equals("gravel")||below.equals("dirt_path")?"ROADBLOCK":"other"),1,Integer::sum); pc.merge(cx+","+cz,1,Integer::sum); if(s.size()<40&&(below.contains("planks")||below.equals("gravel")||below.equals("dirt_path")))s.add("x="+(cx*16+lx)+" z="+(cz*16+lz)+" snow y="+(y+mn[0])+" air "+d+" over "+below);}}
      }}
    System.out.println("chunks "+chunks+" floating snow layers "+tot+" chunks affected "+pc.size()); hist.forEach((k,v)->System.out.println(k+" = "+v)); s.forEach(System.out::println); }
}
