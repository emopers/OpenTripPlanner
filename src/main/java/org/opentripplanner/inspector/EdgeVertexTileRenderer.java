/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.inspector;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.Collection;

import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;

import com.vividsolutions.jts.awt.IdentityPointTransformation;
import com.vividsolutions.jts.awt.PointShapeFactory;
import com.vividsolutions.jts.awt.ShapeWriter;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.buffer.BufferParameters;
import com.vividsolutions.jts.operation.buffer.OffsetCurveBuilder;

/**
 * A TileRenderer implementation which get all edges/vertex in the bounding box of the tile, and
 * call a EdgeVertexRenderer for getting rendering attributes of each (color, string label...).
 * 
 * @author laurent
 */
public class EdgeVertexTileRenderer implements TileRenderer {

    public class EdgeVisualAttributes {

        public Color color;

        public String label;
    }

    public class VertexVisualAttributes {

        public Color color;

        public String label;
    }

    public interface EdgeVertexRenderer {

        /**
         * @param e The edge being rendered.
         * @param attrs The edge visual attributes to fill-in.
         * @return True to render this edge, false otherwise.
         */
        public abstract boolean renderEdge(Edge e, EdgeVisualAttributes attrs);

        /**
         * @param v The vertex being rendered.
         * @param attrs The vertex visual attributes to fill-in.
         * @return True to render this vertex, false otherwise.
         */
        public abstract boolean renderVertex(Vertex v, VertexVisualAttributes attrs);
    }

    public int getColorModel() {
        return BufferedImage.TYPE_INT_ARGB;
    }

    private EdgeVertexRenderer evRenderer;

    public EdgeVertexTileRenderer(EdgeVertexRenderer evRenderer) {
        this.evRenderer = evRenderer;
    }

    public void renderTile(TileRenderContext context) {

        float lineWidth = (float) (1.0f + 5.0f / Math.sqrt(context.metersPerPixel));

        // TODO Grow a bit the envelope to prevent rendering glitches between tiles
        Collection<Vertex> vertices = context.graph.streetIndex
                .getVerticesForEnvelope(context.bbox);
        // TODO Include all edges in the spatial index
        Collection<StreetEdge> edges = context.graph.streetIndex.getEdgesForEnvelope(context.bbox);

        // Note: we do not use the transform inside the shapeWriter, but do it ourselves
        // since it's easier for the offset to work in pixel size.
        ShapeWriter shapeWriter = new ShapeWriter(new IdentityPointTransformation(),
                new PointShapeFactory.Point());
        GeometryFactory geomFactory = new GeometryFactory();

        Stroke stroke = new BasicStroke(lineWidth * 1.4f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_BEVEL);
        Stroke halfStroke = new BasicStroke(lineWidth * 0.6f + 1.0f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_BEVEL);
        Stroke arrowStroke = new ShapeStroke(new Polygon(new int[] { 0, 0, 30 }, new int[] { 0, 20,
                10 }, 3), lineWidth / 2, 5.0f * lineWidth, 2.5f * lineWidth);
        BasicStroke finStroke = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);

        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(lineWidth));
        context.graphics.setFont(font);
        context.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        context.graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        BufferParameters bufParams = new BufferParameters();
        bufParams.setSingleSided(true);
        bufParams.setJoinStyle(BufferParameters.JOIN_BEVEL);

        EdgeVisualAttributes evAttrs = new EdgeVisualAttributes();
        for (Edge edge : edges) {
            evAttrs.color = null;
            evAttrs.label = null;
            Geometry edgeGeom = edge.getGeometry();
            if (edgeGeom == null)
                continue;
            if (!context.bbox.intersects(edgeGeom.getEnvelopeInternal()))
                continue;
            boolean render = evRenderer.renderEdge(edge, evAttrs);
            if (!render)
                continue;

            Geometry midLineGeom = context.transform.transform(edgeGeom);
            OffsetCurveBuilder offsetBuilder = new OffsetCurveBuilder(new PrecisionModel(),
                    bufParams);
            Coordinate[] coords = offsetBuilder.getOffsetCurve(midLineGeom.getCoordinates(),
                    lineWidth * 0.4);
            LineString offsetLine = geomFactory.createLineString(coords);
            Shape midLineShape = shapeWriter.toShape(midLineGeom);
            Shape offsetShape = shapeWriter.toShape(offsetLine);

            context.graphics.setStroke(halfStroke);
            context.graphics.setColor(evAttrs.color);
            // context.graphics.fill(oneSideShape);
            context.graphics.draw(offsetShape);
            if (lineWidth > 6.0f) {
                context.graphics.setColor(Color.WHITE);
                context.graphics.setStroke(arrowStroke);
                context.graphics.draw(offsetShape);
            }
            if (lineWidth > 4.0f) {
                context.graphics.setColor(Color.BLACK);
                context.graphics.setStroke(finStroke);
                context.graphics.draw(midLineShape);
            }
            if (evAttrs.label != null && lineWidth > 8.0f) {
                context.graphics.setColor(Color.BLACK);
                context.graphics.setStroke(new TextStroke("    " + evAttrs.label
                        + "                              ", font, false, true));
                context.graphics.draw(offsetShape);
            }
        }

        VertexVisualAttributes vvAttrs = new VertexVisualAttributes();
        for (Vertex vertex : vertices) {
            vvAttrs.color = null;
            vvAttrs.label = null;
            Point point = geomFactory.createPoint(new Coordinate(vertex.getLon(), vertex.getLat()));
            if (!context.bbox.contains(point.getCoordinate()))
                continue;
            boolean render = evRenderer.renderVertex(vertex, vvAttrs);
            if (!render)
                continue;

            Point tilePoint = (Point) context.transform.transform(point);
            Shape shape = shapeWriter.toShape(tilePoint);

            context.graphics.setColor(vvAttrs.color);
            context.graphics.setStroke(stroke);
            context.graphics.draw(shape);
            if (vvAttrs.label != null && context.metersPerPixel < 1.0) {
                context.graphics.setColor(Color.BLACK);
                context.graphics.drawString(vvAttrs.label, (float) tilePoint.getX(),
                        (float) tilePoint.getY());
            }
        }
    }
}