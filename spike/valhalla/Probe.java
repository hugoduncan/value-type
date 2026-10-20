import java.lang.reflect.*; import java.lang.classfile.*; import java.util.*;
public class Probe { public static void main(String[] a) throws Exception {
  for (Method m : Class.class.getMethods()) if (m.getName().matches("is(Value|Identity).*")) System.out.println("Class." + m);
  System.out.println("Point isValue=" + Point.class.isValue() + " isIdentity=" + Point.class.isIdentity() + " flags=" + Point.class.accessFlags());
  System.out.println("Field flags=" + Point.class.getDeclaredField("x").accessFlags());
  for (AccessFlag f : AccessFlag.values()) if (f.name().matches("IDENTITY|SUPER|STRICT.*")) System.out.println(f + " mask=0x" + Integer.toHexString(f.mask()) + " locs=" + f.locations());
  for (Field f : ClassFile.class.getFields()) if (f.getName().matches("ACC_(IDENTITY|STRICT.*|SUPER)|PREVIEW.*")) System.out.println("ClassFile." + f.getName() + "=0x" + Integer.toHexString(((Number) f.get(null)).intValue()));
  System.out.println("latestMajor=" + ClassFile.latestMajorVersion() + " Runtime feature=" + Runtime.version().feature());
  var p1 = new Point(1, "a"); var p2 = new Point(1, "a");
  System.out.println("p1==p2 " + (p1 == p2) + " equals " + p1.equals(p2) + " hash eq " + (p1.hashCode() == p2.hashCode()));
  try { Object o = p1; synchronized (o) {} } catch (Throwable t) { System.out.println("sync: " + t); }
  try { new java.lang.ref.WeakReference<>(p1); } catch (Throwable t) { System.out.println("weak: " + t); }
  System.out.println(Arrays.toString(ClassFile.StackMapsOption.values()));
}}
