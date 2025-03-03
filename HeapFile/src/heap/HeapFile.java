package heap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;

import diskmgr.DB;
import diskmgr.DiskMgrException;
import diskmgr.FileEntryNotFoundException;
import diskmgr.FileIOException;
import diskmgr.InvalidPageNumberException;
import diskmgr.InvalidRunSizeException;
<<<<<<< HEAD
import global.GlobalConst;
import global.Page;
import global.PageId;
import global.RID;
=======
import global.*;
>>>>>>> origin/main

/**
 * <h3>Minibase Heap Files</h3>
 * A heap file is an unordered set of records, stored on a set of pages. This
 * class provides basic support for inserting, selecting, updating, and deleting
 * records. Temporary heap files are used for external sorting and in other
 * relational operators. A sequential scan of a heap file (via the Scan class)
 * is the most basic access method.
 */
public class HeapFile implements GlobalConst {
  private String fileName;
  private PageId firstPageId;
  private int recCount;
  private PriorityQueue<PageId> freePages; // Tracks pages with free space
/**
   * If the given name already denotes a file, this opens it; otherwise, this
   * creates a new empty file. A null name produces a temporary heap file which
   * requires no DB entry.
   */

    class SortByFreeSpace implements Comparator<PageId> {
        public int compare(PageId a, PageId b) {
            Page pageA = new Page();
            Page pageB = new Page();

            short freeA = 0, freeB = 0;
            try {
                Minibase.BufferManager.pinPage(a, pageA, false);
                HFPage hfA = new HFPage(pageA);
                freeA = hfA.getFreeSpace();
                Minibase.BufferManager.unpinPage(a, false);

                Minibase.BufferManager.pinPage(b, pageB, false);
                HFPage hfB = new HFPage(pageB);
                freeB = hfB.getFreeSpace();
                Minibase.BufferManager.unpinPage(b, false);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return freeA - freeB;
        }
    }
  public HeapFile(String name) {
    fileName = name;
    recCount = 0;
    freePages = new PriorityQueue<>(new SortByFreeSpace());

    firstPageId = Minibase.DiskManager.get_file_entry(name);
    if (firstPageId == null) {
        firstPageId = new PageId();
        Minibase.DiskManager.allocate_page(firstPageId, 1);
        Minibase.DiskManager.add_file_entry(name, firstPageId);

        Page page = new Page();
        Minibase.BufferManager.pinPage(firstPageId, page, true);
        HFPage hfPage = new HFPage(page);
        hfPage.initDefaults();
        hfPage.setCurPage(firstPageId);
        Minibase.BufferManager.unpinPage(firstPageId, true);
    }
  }

  /**
   * Called by the garbage collector when there are no more references to the
   * object; deletes the heap file if it's temporary.
   */
  protected void finalize() throws Throwable {
    if (fileName == null) {  // Deletes the heap file if it's temporary
      deleteFile();
    }
  }

  /**
   * Deletes the heap file from the database, freeing all of its pages.
   */
  public void deleteFile() {
    try {
        DB db = new DB();

        // Retrieve the first page of the file
        PageId firstPageId = db.get_file_entry(fileName);
        
        if (firstPageId == null) {
            System.err.println("File " + fileName + " not found in the database.");
            return;
        }

        // Free all pages in the heap file by traversing nextPage links
        PageId currentPageId = new PageId(firstPageId.pid);
        Page currentPage = new Page();

        while (currentPageId.pid != -1) {
            db.read_page(currentPageId, currentPage); // Read the current page
            HFPage hfPage = new HFPage(currentPage);
            PageId nextPageId = hfPage.getNextPage(); // Get next page before deallocating

            try {
                db.deallocate_page(currentPageId);
            } catch (InvalidRunSizeException | InvalidPageNumberException | IOException | FileIOException | DiskMgrException e) {
                System.err.println("Error deallocating page " + currentPageId.pid + ": " + e.getMessage());
            }

            currentPageId = nextPageId; // Move to the next page
        }

        // Remove file entry from the database
        try {
            db.delete_file_entry(fileName);
        } catch (FileEntryNotFoundException | IOException | FileIOException | InvalidPageNumberException | DiskMgrException e) {
            System.err.println("Error deleting file entry for " + fileName + ": " + e.getMessage());
        }

        // Reset file metadata
        recCount = 0;
        freePages.clear(); // Clear free pages tracking

    } catch (IOException | FileIOException | InvalidPageNumberException | DiskMgrException e) {
        System.err.println("Unexpected error deleting file " + fileName + ": " + e.getMessage());
    }
  }


  /**
   * Inserts a new record into the file and returns its RID.
   * 
   * @throws IllegalArgumentException if the record is too large
   */
  public RID insertRecord(byte[] record) {
    if (record.length > HFPage.MAX_RECORD_SIZE) {
        throw new IllegalArgumentException("Record too large to fit in a heap file page.");
    }

    DB db = new DB();

    PageId targetPageId = null;
    Page targetPage = new Page();
    HFPage hfPage;

    // Step 1: Find a page with free space
    if (!freePages.isEmpty()) {
        targetPageId = freePages.poll();  //Get a free page from tracking
        try {
            db.read_page(targetPageId, targetPage);
            hfPage = new HFPage(targetPage);
        } catch (IOException | FileIOException | InvalidPageNumberException e) {
            System.err.println("Error reading free page: " + e.getMessage());
            return null;
        }
    } else {
        // Step 2: Traverse the heap file if no free pages are tracked
        PageId currentPageId = new PageId(firstPageId.pid);
        Page currentPage = new Page();

        while (currentPageId.pid != -1) {
            try {
                db.read_page(currentPageId, currentPage);
                hfPage = new HFPage(currentPage);
                
                if (hfPage.getFreeSpace() >= record.length) {
                    targetPageId = new PageId(currentPageId.pid);
                    targetPage = currentPage;
                    break;
                }

                currentPageId = hfPage.getNextPage();  // Move to next page
            } catch (IOException | FileIOException | InvalidPageNumberException e) {
                System.err.println("Error reading heap page: " + e.getMessage());
                return null;
            }
        }
    }

    // Step 3: Allocate a new page if no existing page has enough space
    if (targetPageId == null) {
        targetPageId = new PageId();
        try {
            db.allocate_page(targetPageId);
            db.read_page(targetPageId, targetPage);
            hfPage = new HFPage(targetPage);
            hfPage.initDefaults();
            hfPage.setCurPage(targetPageId);

            // Link new page to the heap file
            hfPage.setNextPage(new PageId(-1)); // No next page yet
        } catch (IOException | InvalidPageNumberException | DiskMgrException e) {
            System.err.println("Error allocating new page: " + e.getMessage());
            return null;
        }
    }

    // Step 4: Insert record into the selected page
    RID rid = hfPage.insertRecord(record);
    if (hfPage.getFreeSpace() > 0) {
        freePages.add(targetPageId);  // Track page in free space list
    }

    // Step 5: Write the page back to disk
    try {
        db.write_page(targetPageId, targetPage);
    } catch (IOException | FileIOException | InvalidPageNumberException e) {
        System.err.println("Error writing page after insert: " + e.getMessage());
        return null;
    }

    // Step 6: Update metadata
    recCount++;

    return rid;
  }

  /**
   * Reads a record from the file, given its id.
   * 
   * @throws IllegalArgumentException if the rid is invalid
   */
  public byte[] selectRecord(RID rid) {
    DB db = new DB(); // Use DB instance to interact with disk
    Page targetPage = new Page();
    HFPage hfPage;

    try {
        // Step 1: Read the page where the record is stored
        db.read_page(rid.pageNo, targetPage);
        hfPage = new HFPage(targetPage);

        // Step 2: Retrieve the record
        byte[] record = hfPage.selectRecord(rid);

        return record; //Return the record data

    } catch (IOException | FileIOException | InvalidPageNumberException e) {
        System.err.println("Error selecting record: " + e.getMessage());
        return null;
    }
  }

  /**
   * Updates the specified record in the heap file.
   * 
   * @throws IllegalArgumentException if the rid or new record is invalid
   */
  public void updateRecord(RID rid, byte[] newRecord) {
    DB db = new DB(); //Use DB instance
    Page targetPage = new Page();
    HFPage hfPage;

    try {
        // Step 1: Read the page where the record is stored
        db.read_page(rid.pageNo, targetPage);
        hfPage = new HFPage(targetPage);

        // Step 2: Convert `byte[]` to `Tuple`
        Tuple newTuple = new Tuple(newRecord, 0, newRecord.length);  //Convert byte[] to Tuple

        // Step 3: Update the record inside the page
        hfPage.updateRecord(rid, newTuple);  //Now works!

        // Step 4: Write the updated page back to disk
        db.write_page(rid.pageNo, targetPage);

    } catch (IOException | FileIOException | InvalidPageNumberException e) {
        System.err.println("Error updating record: " + e.getMessage());
    }
  }


  /**
   * Deletes the specified record from the heap file.
   * 
   * @throws IllegalArgumentException if the rid is invalid
   */
  public void deleteRecord(RID rid) {
    DB db = new DB(); //Use DB instance
    Page targetPage = new Page();
    HFPage hfPage;

    try {
        // Step 1: Read the page where the record is stored
        db.read_page(rid.pageNo, targetPage);
        hfPage = new HFPage(targetPage);

        // Step 2: Delete the record inside the page
        hfPage.deleteRecord(rid);  //Remove the record

        // Step 3: Check if the page is empty
        if (hfPage.getSlotCount() == 0) {
            freePages.add(rid.pageNo);  //Mark page as free
        }

        // Step 4: Write the updated page back to disk
        db.write_page(rid.pageNo, targetPage);

    } catch (IOException | FileIOException | InvalidPageNumberException e) {
        System.err.println("Error deleting record: " + e.getMessage());
    }
  }


  /**
   * Gets the number of records in the file.
   */
  public int getRecCnt() {
    DB db = new DB(); //Use DB instance
    int totalRecords = 0;
    PageId currentPageId = new PageId(firstPageId.pid);
    Page currentPage = new Page();

    try {
        while (currentPageId.pid != -1) {
            db.read_page(currentPageId, currentPage);
            HFPage hfPage = new HFPage(currentPage);

            totalRecords += hfPage.getSlotCount(); //Sum up the number of records in the page

            currentPageId = hfPage.getNextPage(); //Move to the next page
        }
    } catch (IOException | FileIOException | InvalidPageNumberException e) {
        System.err.println("Error counting records: " + e.getMessage());
    }
    return totalRecords;
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

} // public class HeapFile implements GlobalConst
