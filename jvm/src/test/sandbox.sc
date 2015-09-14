import hu.mapro.mapping.fit.Fit

val records = Fit.readRecords(getClass.getResource("/test01.fit"))

records foreach {
  r => println(
    r.getPositionLat,
    r.getPositionLong
  )
}
