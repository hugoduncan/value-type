public value class Point {
  final long x; final Object y;
  public Point(long x, Object y) { this.x = x; this.y = y; }
  public long sum() { return x + (y == null ? 0 : 1); }
}
