package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The region field's join rule ({@link FieldRegionView}) seen through {@link RegionView}: a cell belongs to the settlement
 * that claimed it (or that it claimed), with the owner's tier. Site ids are {@code s<cx>_<cz>} of the claimant, so every
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
		if (r.site() == null) return Optional.empty();
		CellKey owner = r.site().owner();
		List<Cell> cells = new ArrayList<>();
		for (CellKey k : r.site().cells()) cells.add(new Cell(k.cx(), k.cz()));
		cells.sort(CellGeometry::compare);
		Tier tier = view.decision(owner).tier();
		return Optional.of(new SettlementSite(Ids.site(owner.cx(), owner.cz()), tier, new Pt(r.site().centreX(), r.site().centreZ()),
				r.site().radius(), new Cell(owner.cx(), owner.cz()), cells));
	}
}
