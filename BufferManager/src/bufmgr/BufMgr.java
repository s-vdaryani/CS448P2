package bufmgr;

import chainexception.ChainException;
import global.Page;
import global.PageId;
import global.SystemDefs;

import java.io.IOException;
import java.util.LinkedList;
public class BufMgr {
    private BuffDesc[] desc;
    private LinkedList<Integer> replacementList;
    private Page[] frs;
    private HashMap hashFrame;
    public BufMgr(int numbufs, String replacementPolicy) {
        frs = new Page[numbufs];

        replacementList = new LinkedList<Integer>();
        
        hashFrame = new HashMap(numbufs);

        desc = new BuffDesc[numbufs];

        for (int i = 0; i < numbufs; i++) {
            desc[i] = new BuffDesc();
        }
        for (int j = 0; j < numbufs; j++) {
            replacementList.addLast(j);
        }
        for (int k = 0; k < numbufs; k++) {
            frs[k] = new Page();
        }
    }

    public void pinPage(PageId pageno, Page page, boolean emptyPage)  throws ChainException, IOException {
        //System.out.println("pinPage2");
        Integer frame = hashFrame.get(pageno);
        if (frame != null) {
            if (desc[frame].cnt == 0)
                replacementList.remove(frame);
            desc[frame].cnt++;
            page.setpage(frs[frame].getpage());
        }
        else {
            if (replacementList.size() - 1 == 0) {
                throw new BufferPoolExceededException(new Exception(), "buffer is full");
            }

            frame = replacementList.pollFirst();
            PageId oldPageId = new PageId(desc[frame].pageNumber);
            if (desc[frame].isDirty) {
                SystemDefs.JavabaseDB.write_page(oldPageId, frs[frame]);
            }
            hashFrame.remove(oldPageId);
            SystemDefs.JavabaseDB.read_page(pageno, page);
            hashFrame.put(new PageId(pageno.pid), frame);
            frs[frame].setpage(page.getpage());
            desc[frame] = new BuffDesc(pageno.pid, 1, false);
        }
    }

    public void unpinPage(PageId pageno, boolean dirty) throws ChainException {
        Integer pageNumber = hashFrame.get(pageno);
        if (pageNumber == null) {
            throw new HashEntryNotFoundException(new Exception(), "HashEntryNotFoundException");
        }
        else {
            if (desc[pageNumber].cnt <= 0) {
                throw new ChainException(new Exception(), "PageUnpinnedException");
            }
            else {
                desc[pageNumber].isDirty = dirty;
                desc[pageNumber].cnt--;

                if (desc[pageNumber].cnt == 0) {
                    if (!replacementList.contains(pageNumber)) {
                        replacementList.addFirst(pageNumber);
                    }
                }
            }

        }
    }

    public PageId newPage(Page firstPage, int howmany) {
        PageId newPid = new PageId();
        try {
            SystemDefs.JavabaseDB.allocate_page(newPid, howmany);
            pinPage(newPid, firstPage, false);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ChainException e) {
            e.printStackTrace();
        }
        return newPid;
    }

    public void freePage(PageId globalPageId) throws ChainException {
        Integer frame = hashFrame.get(globalPageId);
        if (frame != null) {
            if (desc[frame].cnt > 1) {
                throw new PagePinnedException(new Exception(), "Page is pinned");
            }
            else {
                if (desc[frame].cnt == 1)
                    unpinPage(globalPageId, false);
                try {
                    SystemDefs.JavabaseDB.deallocate_page(globalPageId);
                } catch (ChainException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                desc[frame] = new BuffDesc();
                hashFrame.remove(globalPageId);

                replacementList.remove(frame);
            }
        }
    }

    public void flushPage(PageId pageid) {
        Integer f = hashFrame.get(pageid);

        if (f == null) {
            System.err.println("Error: flushing a page not in the buffer pool: " + pageid.pid);
            return;
        }

        try {
            SystemDefs.JavabaseDB.write_page(pageid, frs[f]);
            desc[f].isDirty = false;
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void flushAllPages() {
        for (int i = 0; i < desc.length; i++) {
            if (desc[i].isDirty) {
                PageId pid = new PageId(desc[i].pageNumber);
                if (hashFrame.get(pid) != null) {
                    flushPage(pid);
                }
            }
        }
    }

    public int getNumBuffers() {
        return frs.length;
    }
    public int getNumUnpinnedBuffers() {
        int count = -1;
        for (int i = 0; i < desc.length; i++) {
            if (desc[i].cnt == 0)
                count++;
        }
        return count;
    }

}
