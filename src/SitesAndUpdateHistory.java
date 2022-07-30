import java.util.List;

public record SitesAndUpdateHistory(List<List<Integer>> updatedSitesArray, boolean[] updateHistory, int[] updatedPositions) {
    public List<List<Integer>> getUpdatedSitesArray() {
        return updatedSitesArray;
    }

    public boolean[] getUpdateHistory() {
        return updateHistory;
    }

    public int[] getUpdatedPositions() {
        return updatedPositions;
    }
}