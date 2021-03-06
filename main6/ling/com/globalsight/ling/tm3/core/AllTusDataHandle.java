package com.globalsight.ling.tm3.core;

import java.sql.SQLException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A handle to the entire TM.
 */
class AllTusDataHandle<T extends TM3Data> extends AbstractDataHandle<T> {
	
	private Set<String> m_jobAttributeSet;

    AllTusDataHandle(BaseTm<T> tm) {
        super(tm);
    }
    
    AllTusDataHandle(BaseTm<T> tm, Date start, Date end) {
        super(tm, start, end);
    }
    
    AllTusDataHandle(BaseTm<T> tm, Date start, Date end,Set<String> jobAttributeSet) {
        super(tm, start, end);
        m_jobAttributeSet = jobAttributeSet;
    }

    @Override
    public long getCount() throws TM3Exception {
        try {
        	if(m_jobAttributeSet == null || m_jobAttributeSet.size() == 0)
        	{
        		return getTm().getStorageInfo().getTuStorage()
        			.getTuCount(getStart(), getEnd());
        	}
        	else
        	{
        		return getTm().getStorageInfo().getTuStorage()
        			.getTuCount(getStart(), getEnd(), m_jobAttributeSet);
        	}
        } catch (SQLException e) {
            throw new TM3Exception(e);
        }
    }

    @Override
    public long getTuvCount() throws TM3Exception {
        try {
            return getTm().getStorageInfo().getTuStorage()
                .getTuvCount(getStart(), getEnd());
        } catch (SQLException e) {
            throw new TM3Exception(e);
        }
    }

    @Override
    public void purgeData() throws TM3Exception {
        try {
            getTm().getStorageInfo().getTuStorage()
                .deleteTus(getStart(), getEnd());
        } catch (SQLException e) {
            throw new TM3Exception(e);
        }
    }
    
    @Override
    public Iterator<TM3Tu<T>> iterator() throws TM3Exception {
        return new AllTusIterator();
    }

    class AllTusIterator extends AbstractDataHandle<T>.TuIterator {
        @Override
        protected void loadPage() {
            try {
                // Load 100 at a time
                List<TM3Tu<T>> page;
                if(m_jobAttributeSet == null || m_jobAttributeSet.size() == 0)
                {
                	page= getTm().getStorageInfo().getTuStorage()
                		.getTuPage(startId, 100, getStart(), getEnd());
                }
                else
                {
                	page= getTm().getStorageInfo().getTuStorage()
            			.getTuPage(startId, 100, getStart(), getEnd(), m_jobAttributeSet);
                }
                if (page.size() > 0) {
                    startId = page.get(page.size() - 1).getId();
                    currentPage = page.iterator();
                }
            }
            catch (SQLException e) {
                throw new TM3Exception(e);
            }
        }
    }
}
