/* ... */

package bufmgr;

import java.io.*;
import java.util.*;

import chainexception.ChainException;
import com.sun.net.httpserver.Filter;
import diskmgr.*;
import global.*;

public class BufMgr implements GlobalConst{
  
    // Buffer pool (array of Page objects stored in memory)
    private Page[] bufPool;

    // Frame Table - stores metadata about each frame
    private FrameDesc[] frameTable;

    // HashMap to track which page is in which frame (PageId → Frame index)
    private HashMap<Integer, Integer> pageTable;

    // Queue for FIFO (First-In-First-Out) page replacement
    private Queue<Integer> fifoQueue;

    // Number of buffers (frames)
    private int numBuffers;

    // Frame Descriptor class - stores metadata about each frame
    private static class FrameDesc {
      int pageNumber;  // Page ID stored in this frame (-1 if empty)
      int pinCount;    // How many times the page is pinned
      boolean dirty;   // Whether the page has been modified

      public FrameDesc() {
        this.pageNumber = -1;  // -1 means "empty frame"
        this.pinCount = 0;
        this.dirty = false;
      }
    }

  /**
   * Create the BufMgr object.
   * Allocate pages (frames) for the buffer pool in main memory and
   * make the buffer manage aware that the replacement policy is
   * specified by replacerArg.
   *
   * @param numbufs number of buffers in the buffer pool.
   * @param replacerArg name of the buffer replacement policy.
   */

  public BufMgr(int numbufs, String replacerArg) {
    //YOUR CODE HERE
    this.numBuffers = numbufs;
    bufPool = new Page[numbufs];
    frameTable = new FrameDesc[numbufs];
    pageTable = new HashMap<>();
    fifoQueue = new LinkedList<>();

    // Initialize frames
    for (int i = 0; i < numbufs; i++) {
        bufPool[i] = new Page();
        frameTable[i] = new FrameDesc();
    }
  }


  /**
   * Pin a page.
   * First check if this page is already in the buffer pool.
   * If it is, increment the pin_count and return a pointer to this
   * page.  If the pin_count was 0 before the call, the page was a
   * replacement candidate, but is no longer a candidate.
   * If the page is not in the pool, choose a frame (from the
   * set of replacement candidates) to hold this page, read the
   * page (using the appropriate method from {diskmgr} package) and pin it.
   * Also, must write out the old page in chosen frame if it is dirty
   * before reading new page.  (You can assume that emptyPage==false for
   * this assignment.)
   *
   * @param pin_pgid page number in the minibase.
   * @param page the pointer poit to the page.
   * @param emptyPage true (empty page); false (non-empty page)
   */

  public void pinPage(PageId pin_pgid, Page page, boolean emptyPage) throws ChainException {
    //YOUR CODE HERE

  }


  /**
   * Unpin a page specified by a pageId.
   * This method should be called with dirty==true if the client has
   * modified the page.  If so, this call should set the dirty bit
   * for this frame.  Further, if pin_count>0, this method should
   * decrement it. If pin_count=0 before this call, throw an exception
   * to report error.  (For testing purposes, we ask you to throw
   * an exception named PageUnpinnedException in case of error.)
   *
   * @param PageId_in_a_DB page number in the minibase.
   * @param dirty the dirty bit of the frame
   */

    private int findAvailableFrame() {
        // checking for an empty frame
        for (int i = 0; i < numBuffers; i++) {
            if (frameTable[i].pageNumber == -1) {
                return i; //index of empty page
            }
        }

        // if no available frame
        int size = fifoQueue.size();
        while (size > 0) {
            int frame = fifoQueue.poll(); //getting oldest frame for fifo

            // frame should be unpinned before replacement
            if (frameTable[frame].pinCount == 0) {
                return frame;
            }

            
            fifoQueue.add(frame);
            size--;
        }

        return -1; // no frame available
    }
  
  public void unpinPage(PageId PageId_in_a_DB, boolean dirty) throws ChainException {
      //YOUR CODE HERE
  }


  /**
   * Allocate new pages.
   * Call DB object to allocate a run of new pages and
   * find a frame in the buffer pool for the first page
   * and pin it. (This call allows a client of the Buffer Manager
   * to allocate pages on disk.) If buffer is full, i.e., you
   * can't find a frame for the first page, ask DB to deallocate
   * all these pages, and return null.
   *
   * @param firstpage the address of the first page.
   * @param howmany total number of allocated new pages.
   *
   * @return the first page id of the new pages.  null, if error.
   */

  public PageId newPage(Page firstpage, int howmany) {
    // Haley
    PageId returnedPage = new PageId();
    return returnedPage;
  }


  /**
   * This method should be called to delete a page that is on disk.
   * This routine must call the method in diskmgr package to
   * deallocate the page.
   *
   * @param globalPageId the page number in the data base.
   */

  public void freePage(PageId globalPageId) throws ChainException {
      // Haley
  }


  /**
   * Used to flush a particular page of the buffer pool to disk.
   * This method calls the write_page method of the diskmgr package.
   *
   * @param pageid the page number in the database.
   */

   public void flushPage(PageId pageid) {
    if (!pageTable.containsKey(pageid.pid)) {
        return;
    }
    int frameIndex = pageTable.get(pageid.pid);
    if (frameTable[frameIndex].dirty) {
        try {
            SystemDefs.JavabaseDB.write_page(pageid, bufPool[frameIndex]);
            frameTable[frameIndex].dirty = false;
        } catch (InvalidPageNumberException | FileIOException | IOException e) {
            System.err.println("Error writing page " + pageid.pid + " to disk: " + e.getMessage());
        }
    }
  }


  /** Flushes all pages of the buffer pool to disk
   */

   public void flushAllPages() {
    for (int i = 0; i < numBuffers; i++) {
      if (frameTable[i].dirty) {
          try {
              PageId pid = new PageId(frameTable[i].pageNumber);  // ✅ Create a valid PageId
              SystemDefs.JavabaseDB.write_page(pid, bufPool[i]);  // ✅ Use correct PageId format
              frameTable[i].dirty = false;
          } catch (InvalidPageNumberException | FileIOException | IOException e) {
              System.err.println("Error flushing page " + frameTable[i].pageNumber + " to disk: " + e.getMessage());
          }
      }
    }
  }


  /** Gets the total number of buffers.
   *
   * @return total number of buffer frames.
   */

  public int getNumBuffers() {
      return numBuffers;
  }


  /** Gets the total number of unpinned buffer frames.
   *
   * @return total number of unpinned buffer frames.
   */

  public int getNumUnpinnedBuffers() {
    // Haley
    return 0;
  }

}

