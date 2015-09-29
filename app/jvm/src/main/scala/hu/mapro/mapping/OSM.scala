package hu.mapro.mapping

/**
 * Created by pappmar on 23/09/2015.
 */
object OSM {

  def xml(tracks: Seq[Seq[Position]], highway: String) = {
    val (tracksWithId, _) = tracks.foldLeft((Seq():Seq[Seq[(Position, Long)]], 0:Long)) {
      case ((acc, idx), next) =>
        val (nextWithIdsRev, newIdx) = next.foldLeft((Seq():Seq[(Position, Long)], idx)) {
          case ((acc2, idx2), next2) => ((next2, idx2) +: acc2, idx2+1)
        }
        (nextWithIdsRev.reverse +: acc, newIdx)
    }
    <osm>
      <bounds
        minlon={tracks.flatten.map(_.lon).min.toString}
        maxlon={tracks.flatten.map(_.lon).max.toString}
        minlat={tracks.flatten.map(_.lat).min.toString}
        maxlat={tracks.flatten.map(_.lat).max.toString}
      />
      {
        for {
          trackWithId <- tracksWithId
          (pos, id) <- trackWithId
        } yield <node id={id.toString} lat={pos.lat.toString} lon={pos.lon.toString}></node>
      }
      {
        for {
          (track, trackId) <- tracksWithId.zipWithIndex
        } yield
          <way id={trackId.toString} visible="true">
            {
              for {
                (pos, posId) <- track
              } yield <nd ref={posId.toString}/>
            }
            <tag k="highway" v={highway}/>
          </way>
      }
    </osm>
  }


}
