package heap;

import global.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * <h3>Minibase Heap Files</h3>
 * A heap file is an unordered set of records, stored on a set of pages. This
 * class provides basic support for inserting, selecting, updating, and deleting
 * records. Temporary heap files are used for external sorting and in other
 * relational operators. A sequential scan of a heap file (via the Scan class)
 * is the most basic access method.
 */
public class HeapFile implements GlobalConst {
    protected ArrayList<PageId> pageList;     // List of all page IDs in this heap file
    private ArrayList<Integer> pageIds;       // List of numeric page IDs for quick lookup
    private int recordCount;                  // Total number of records in the heap file
    private HFPage currentPage;               // Reference to the current page being accessed
    private int status;                       // Status flag (0 = active, 1 = deleted)
    private String fileName;                  // Name of the heap file on disk

    /**
     * Constructor for the HeapFile class.
     * Creates a new heap file if it doesn't exist, or opens an existing one.
     *
     * @param fileName The name of the heap file to create or open
     */
    public HeapFile(String fileName) {
        this.fileName = fileName;
        this.status = 0;                      // Initialize as active
        this.recordCount = 0;                 // Start with zero records
        pageList = new ArrayList<>();         // Initialize page lists
        pageIds = new ArrayList<>();

        Page initialPage = new Page();        // Create a new buffer page

        if (fileName != null) {
            //try to get the first page ID for the file if it exists
            PageId firstPageId = Minibase.DiskManager.get_file_entry(fileName);

            if (firstPageId == null) {
                //file doesn't exist, create a new file with one page
                firstPageId = Minibase.BufferManager.newPage(initialPage, 1);
                Minibase.DiskManager.add_file_entry(fileName, firstPageId);
                Minibase.BufferManager.unpinPage(firstPageId, UNPIN_DIRTY);

                // add the page to our tracking lists
                pageList.add(firstPageId);
                pageIds.add(firstPageId.pid);

                //i/nitialize the first page as an HFPage
                Minibase.BufferManager.pinPage(firstPageId, initialPage, PIN_DISKIO);
                currentPage = new HFPage(initialPage);
                currentPage.initDefaults();
                currentPage.setCurPage(firstPageId);
                Minibase.BufferManager.unpinPage(firstPageId, UNPIN_DIRTY);
                return;
            }

            // file exists --> load the first page
            Minibase.BufferManager.pinPage(firstPageId, initialPage, PIN_DISKIO);
            currentPage = new HFPage(initialPage);
            currentPage.setCurPage(firstPageId);
            currentPage.setData(initialPage.getData());
            pageList.add(firstPageId);
            pageIds.add(firstPageId.pid);
            Minibase.BufferManager.unpinPage(firstPageId, UNPIN_CLEAN);

            //count records in the first page
            RID recordId;
            for (recordId = currentPage.firstRecord(); recordId != null; recordId = currentPage.nextRecord(recordId)) {
                recordCount++;
            }

            //load all subsequent pages in the file
            PageId nextPage = currentPage.getNextPage();
            while (nextPage.pid > 0) {
                HFPage nextHeapPage = new HFPage();
                Minibase.BufferManager.pinPage(nextPage, nextHeapPage, PIN_DISKIO);
                pageList.add(nextPage);
                pageIds.add(nextPage.pid);

                //count records in this page
                for (recordId = nextHeapPage.firstRecord(); recordId != null; recordId = nextHeapPage.nextRecord(recordId)) {
                    recordCount++;
                }

                Minibase.BufferManager.unpinPage(nextPage, UNPIN_CLEAN);
                nextPage = nextHeapPage.getNextPage();
            }
            return;
        }

        //handle case where fileName is null (temporary heap file)
        PageId tempId = Minibase.DiskManager.get_file_entry(null);
        currentPage = new HFPage(initialPage);
        currentPage.setCurPage(tempId);
        pageList.add(tempId);
        pageIds.add(tempId.pid);
        Minibase.BufferManager.unpinPage(tempId, UNPIN_DIRTY);
    }

    /**
     * TODO: what's the difference between deleteFile and finalize?
     * Called by the garbage collector when there are no more references to the
     * object; deletes the heap file if it's temporary.
     */
    protected void finalize() throws Throwable {
        if (fileName == null) {
            deleteFile();
        }
    }

    /**
     * Deletes the heap file from the database, freeing all of its pages.
     */
    public void deleteFile() {
        if (status == 0) {  // Only delete if the file is still active
            // Deallocate all pages in the file
            for (PageId id : pageList) {
                Minibase.DiskManager.deallocate_page(id);
            }
            Minibase.DiskManager.delete_file_entry(fileName);
            recordCount = 0;
            status = 1;     // Mark as deleted
            pageList = null;
            pageIds = null;
        }
    }

    /**
     * Inserts a new record into the file and returns its RID.
     *
     * @throws IllegalArgumentException if the record is too large
     */
    public RID insertRecord(byte[] record) throws Exception {
        //check if the record is too large for a page
        if (record.length + HFPage.HEADER_SIZE > 1024) {
            throw new SpaceNotAvailableException("Record too large");
        }

        //try to insert into the last page (which likely has the most free space)
        Page diskPage = new Page();
        PageId availablePage = pageList.get(pageList.size() - 1);
        Minibase.BufferManager.pinPage(availablePage, diskPage, PIN_DISKIO);
        HFPage heapPage = new HFPage(diskPage);
        heapPage.setCurPage(availablePage);

        //if there's enough space in the existing page, insert there
        if (heapPage.getFreeSpace() > record.length) {
            RID rid = heapPage.insertRecord(record);
            recordCount++;
            Minibase.BufferManager.unpinPage(availablePage, UNPIN_DIRTY);
            return rid;
        } else {
            Minibase.BufferManager.unpinPage(availablePage, UNPIN_CLEAN);
        }

        //need to create a new page
        Page newPage = new Page();
        PageId newPageId = Minibase.BufferManager.newPage(newPage, 1);
        HFPage newHeapPage = new HFPage(newPage);
        newHeapPage.initDefaults();
        newHeapPage.setCurPage(newPageId);

        //link the new page to the current page
        currentPage.setNextPage(newPageId);
        newHeapPage.setPrevPage(currentPage.getCurPage());

        //insert the record into the new page
        RID newRid = newHeapPage.insertRecord(record);
        pageList.add(newPageId);
        pageIds.add(newPageId.pid);
        currentPage = newHeapPage;
        recordCount++;

        Minibase.BufferManager.unpinPage(newPageId, UNPIN_DIRTY);

        //sort pages by available space for efficient future insertions
        Collections.sort(pageList, new PageSpaceComparator());
        return newRid;
    }

    /**
     * Reads a record from the file, given its id.
     *
     * @throws IllegalArgumentException if the rid is invalid
     */
    public Tuple getRecord(RID rid) throws Exception {
        PageId pageId = rid.pageno;
        // Check if the page exists in this heap file
        if (!pageIds.contains(pageId.pid)) {
            throw new IllegalArgumentException("Invalid RID");
        }

        //find the page and retrieve the record
        for (PageId pid : pageList) {
            if (pid.pid == pageId.pid) {
                HFPage heapPage = new HFPage();
                Minibase.BufferManager.pinPage(pageId, heapPage, false);
                Minibase.BufferManager.unpinPage(pageId, false);
                try {
                    byte[] data = heapPage.selectRecord(rid);
                    return new Tuple(data, 0, data.length);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid RID");
                }
            }
        }
        throw new IllegalArgumentException("Invalid RID");
    }

    /**
     * Updates the specified record in the heap file.
     *
     * @throws IllegalArgumentException if the rid or new record is invalid
     */
    public boolean updateRecord(RID rid, Tuple updatedRecord) throws Exception {
        HFPage heapPage = new HFPage();
        PageId pageId = rid.pageno;
        Minibase.BufferManager.pinPage(pageId, heapPage, false);
        try {
            heapPage.updateRecord(rid, updatedRecord);
            Minibase.BufferManager.unpinPage(pageId, false);
        } catch (IllegalArgumentException e) {
            throw new InvalidUpdateException();
        }
        return true;
    }

    /**
     * Deletes the specified record from the heap file.
     *
     * @throws IllegalArgumentException if the rid is invalid
     */
    public boolean deleteRecord(RID rid) throws Exception {
        HFPage heapPage = new HFPage();
        PageId pageId = rid.pageno;
        Minibase.BufferManager.pinPage(pageId, heapPage, false);
        try {
            heapPage.deleteRecord(rid);
            Minibase.BufferManager.unpinPage(pageId, false);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid RID");
        }
        return true;
    }

    /**
     * Gets the number of records in the file.
     */
    public int getRecCnt() {
        return recordCount;
    }

    /**
     * Initiates a sequential scan of the heap file.
     */
    public HeapScan openScan() {
        return new HeapScan(this);
    }

    /**
     * Returns the name of the heap file.
     */
    public String toString() {
        return this.fileName;
    }
}

/**
 * Comparator class that sorts PageId objects based on their available free space.
 * Pages are compared by pinning them and checking their free space,
 * then unpinning them immediately afterward.
 * Returns a positive value if the first page has more space than the second,
 * negative if it has less, and zero if they have equal space.
 */
class PageSpaceComparator implements Comparator<PageId> {

    public int compare(PageId first, PageId second) {
        short firstSpace;
        HFPage firstPage = new HFPage();
        Minibase.BufferManager.pinPage(first, firstPage, GlobalConst.PIN_DISKIO);
        Minibase.BufferManager.unpinPage(first, GlobalConst.UNPIN_CLEAN);
        firstSpace = firstPage.getFreeSpace();

        short secondSpace;
        HFPage secondPage = new HFPage();
        Minibase.BufferManager.pinPage(second, secondPage, GlobalConst.PIN_DISKIO);
        Minibase.BufferManager.unpinPage(second, GlobalConst.UNPIN_CLEAN);
        secondSpace = secondPage.getFreeSpace();

        return firstSpace - secondSpace;
    }
}