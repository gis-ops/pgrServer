/**
 * パッケージ名：org.pgrserver.vrp
 * ファイル名  ：MainVrp.java
 * 
 * @author mbasa
 * @since Nov 25, 2020
 */
package org.pgrserver.vrp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pgrserver.bean.VrpParamBean;
import org.pgrserver.bean.VrpServiceBean;
import org.pgrserver.bean.VrpVehicleBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.End;
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter.Print;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;

/**
 * 説明：
 *
 */
public class MainVrp {

    private final Logger logger = LoggerFactory.getLogger(MainVrp.class);

    /**
     * コンストラクタ
     *
     */
    public MainVrp() {
    }

    public Map<String,Object> createPlan( VrpParamBean vrpParamBean ) {
        VehicleRoutingProblemSolution solution = planSolver( vrpParamBean );
                
        Map<String,Object> retVal = new LinkedHashMap<String, Object>();
        retVal.put("status", "ok");
        
        Map<String,Object> solutionMap = new LinkedHashMap<String, Object>();
        solutionMap.put("costs", solution.getCost());
        solutionMap.put("noVehicles", vrpParamBean.getVehicles().size());
        solutionMap.put("noRoutes", solution.getRoutes().size());
        solutionMap.put("unassignedJobs", solution.getUnassignedJobs().size() );
        retVal.put("solution", solutionMap);
        
        
        Map<String,Object> dSolutionMap = new LinkedHashMap<String, Object>();
        List<Object> routes     = new ArrayList<Object>();
        List<Object> unAssigned = new ArrayList<Object>();
        
        dSolutionMap.put("routes", routes);
        dSolutionMap.put("unassigned", unAssigned);
        retVal.put("detailed_solution", dSolutionMap);
        
        
        if( !solution.getRoutes().isEmpty() ) {
            int routeCnt = 1;
            for( VehicleRoute route : solution.getRoutes() ) {
                
                
                Map<String,Object> mRoute = new LinkedHashMap<String, Object>();
                List<Object> mRouteParams = new ArrayList<Object>();
                
                mRoute.put("route"+routeCnt, mRouteParams);
                routes.add(mRoute);
                               
                String vehicleId = route.getVehicle().getId();
                
                Map<String,Object> mStartParam = 
                        new LinkedHashMap<String, Object>();
                Start start = route.getStart();
                mStartParam.put("vehicle", vehicleId);
                mStartParam.put("job", start.getName());
                mStartParam.put("capacity", start.getSize().get(0));
                mStartParam.put("lng", start.getLocation().getCoordinate().getX());
                mStartParam.put("lat", start.getLocation().getCoordinate().getY());
                
                mRouteParams.add(mStartParam);
                
                for(TourActivity ta : route.getActivities() ) {
                    Map<String,Object> mRouteParam = 
                            new LinkedHashMap<String, Object>();
                    
                    mRouteParam.put("vehicle", vehicleId);
                    
                    if( ta instanceof TourActivity.JobActivity ) {
                        Job job =  ((TourActivity.JobActivity) ta).getJob();
                        mRouteParam.put("job", job.getId() );
                    }
                    
                    mRouteParam.put("capacity", 
                            ta.getSize().get(0) );
                    
                    Coordinate coord = ta.getLocation().getCoordinate();                    
                    mRouteParam.put("lng", coord.getX());
                    mRouteParam.put("lat", coord.getY());
                    
                    mRouteParams.add(mRouteParam);
                }
                
                Map<String,Object> mEndParam = 
                        new LinkedHashMap<String, Object>();
                End end = route.getEnd();
                mEndParam.put("vehicle", vehicleId);
                mEndParam.put("job", end.getName());
                mEndParam.put("capacity", end.getSize().get(0));
                mEndParam.put("lng", end.getLocation().getCoordinate().getX());
                mEndParam.put("lat", end.getLocation().getCoordinate().getY());
                
                mRouteParams.add(mEndParam);
                
                routeCnt ++;
            }
        }

        if( !solution.getUnassignedJobs().isEmpty() ) {          
            for( Job uJob : solution.getUnassignedJobs() ) {
                unAssigned.add(uJob.getId());
            }
        }
        
        return retVal;
    }
    
    public VehicleRoutingProblemSolution planSolver( 
            VrpParamBean vrpParamBean )  {
        logger.info("Solving Plan");

        VehicleRoutingProblem.Builder vrpBuilder = 
                VehicleRoutingProblem.Builder.newInstance();

        int cnt = 0;
        
        for( VrpVehicleBean v : vrpParamBean.getVehicles() ) {
            logger.info("Vehicle Capacity:"+v.getCapacity());
            cnt++;
            /**
             * VehicleType
             */
            VehicleTypeImpl.Builder vehicleTypeBuilder = 
                    VehicleTypeImpl.Builder.newInstance("vehicleType"+cnt);
            vehicleTypeBuilder.addCapacityDimension(
                    v.getWeightIndex(), v.getCapacity());
            
            VehicleType vehicleType = vehicleTypeBuilder.build();
            /**
             * VehicleBuilder
             */
            VehicleImpl.Builder vehicleBuilder = 
                    VehicleImpl.Builder.newInstance("vehicle"+cnt);
            vehicleBuilder.setStartLocation(Location.newInstance(
                    v.getStartLocation().getLng(),
                    v.getStartLocation().getLat()));
            
            vehicleBuilder.setType(vehicleType);
            /**
             * Adding Vehicle to VrpBuilder
             */
            VehicleImpl vehicle = vehicleBuilder.build();       
            vrpBuilder.addVehicle( vehicle );
        }

        cnt = 0;
        for( VrpServiceBean s : vrpParamBean.getServices() ) {
           cnt ++;
           /**
            * Adding Services
            */
           Service service = Service.Builder
                   .newInstance("service"+cnt)
                   .addSizeDimension(s.getWeightIndex(), s.getCapacity())
                   .setLocation(Location.newInstance(
                           s.getLocation().getLng(),s.getLocation().getLat()))
                   .build();
           
           vrpBuilder.addJob(service);
        }

        VehicleRoutingProblem problem = vrpBuilder.build();
        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);

        Collection<VehicleRoutingProblemSolution> solutions = 
                algorithm.searchSolutions();

        VehicleRoutingProblemSolution bestSolution = 
                Solutions.bestOf(solutions);
        
        SolutionPrinter.print(problem,bestSolution,Print.VERBOSE);
        return bestSolution;
    }
}