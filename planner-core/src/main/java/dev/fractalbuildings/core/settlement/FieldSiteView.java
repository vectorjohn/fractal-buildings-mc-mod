package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.region.CellDecision;
import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The region field's join rule ({@link FieldRegionView}) seen through {@link RegionView}: a cell belongs to the settlement
 * that claimed it (or that it claimed), with the owner's tier; a settled cell nobody claimed is a one-cell settlement of its own. Site ids are {@code s<cx>_<cz>} of the claimant, so every
 * member cell answers with the same site.
 */
public final class FieldSiteView implements RegionView {
	private final FieldRegionView view;
	private final RegionField field;

	public FieldSiteView(FieldRegionView view, RegionField field) {
		this.view = view;
		this.field = field;
	}

	@Override public RegionField field() { return field; }

	@Override public Optional<SettlementSite> siteOf(Cell cell) {
		FieldRegionView.Resolved r = view.cell(cell.cx(), cell.cz());
		if (r.site() == null) {
			// decides alone: a settled hamlet, village or town (or a dropped claimant) is its own one-cell settlement
			CellDecision d = r.decision();
			if (!d.settled()) return Optional.empty();
			Cell self = new Cell(cell.cx(), cell.cz());
			return Optional.of(new SettlementSite(Ids.site(cell.cx(), cell.cz()), d.tier(), new Pt(d.centreX(), d.centreZ()), Math.max(1, d.radius()), self, List.of(self)));
		}
		CellKey owner = r.site().owner();
		List<Cell> cells = new ArrayList<>();
		for (CellKey k : r.site().cells()) cells.add(new Cell(k.cx(), k.cz()));
		cells.sort(CellGeometry::compare);
		Tier tier = view.decision(owner).tier();
		return Optional.of(new SettlementSite(Ids.site(owner.cx(), owner.cz()), tier, new Pt(r.site().centreX(), r.site().centreZ()),
				r.site().radius(), new Cell(owner.cx(), owner.cz()), cells));
	}
}
